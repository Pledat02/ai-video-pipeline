"""Local MCP Streamable HTTP image server backed by ComfyUI. Standard library only."""
import base64
import json
import os
import time
import urllib.parse
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.getenv("MCP_HOST", "127.0.0.1")
PORT = int(os.getenv("MCP_PORT", "8189"))
COMFYUI_URL = os.getenv("COMFYUI_URL", "http://127.0.0.1:8188").rstrip("/")
UNET_MODEL = os.getenv("COMFYUI_UNET_MODEL", "z_image_turbo_bf16.safetensors")
CLIP_MODEL = os.getenv("COMFYUI_CLIP_MODEL", "qwen_3_4b.safetensors")
VAE_MODEL = os.getenv("COMFYUI_VAE_MODEL", "ae.safetensors")
TIMEOUT = int(os.getenv("COMFYUI_TIMEOUT_SECONDS", "180"))
COMFYUI_INPUT_DIR = os.getenv("COMFYUI_INPUT_DIR", r"C:\Users\ADMIN\AppData\Local\Comfy-Desktop\ComfyUI-Shared\input")


def http_json(url, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
        return json.loads(response.read())


def workflow(args):
    # Phrase this positively. The text encoder has no notion of negation, so naming the
    # forbidden layouts ("collage", "grid", "storyboard sheet", "comic panels") here makes
    # the model draw exactly those: at a fixed seed the negated wording produced a 7-panel
    # collage, while this wording produced a single clean frame.
    single_frame_rule = (
        " Single full-frame cinematic still, one continuous camera view filling the entire frame."
    )
    positive_prompt = str(args.get("prompt", "")) + single_frame_rule
    graph = {
        "28": {"class_type": "UNETLoader", "inputs": {"unet_name": UNET_MODEL, "weight_dtype": "default"}},
        "30": {"class_type": "CLIPLoader", "inputs": {"clip_name": CLIP_MODEL, "type": "lumina2", "device": "default"}},
        "29": {"class_type": "VAELoader", "inputs": {"vae_name": VAE_MODEL}},
        "27": {"class_type": "CLIPTextEncode", "inputs": {"text": positive_prompt, "clip": ["30", 0]}},
        "33": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["27", 0]}},
        "13": {"class_type": "EmptySD3LatentImage", "inputs": {
            "width": int(args.get("width", 1024)), "height": int(args.get("height", 1024)), "batch_size": 1}},
        "11": {"class_type": "ModelSamplingAuraFlow", "inputs": {"model": ["28", 0], "shift": 3.0}},
        "3": {"class_type": "KSampler", "inputs": {
            "seed": int(args.get("seed", 1)), "steps": int(args.get("steps", 8)), "cfg": 1.0,
            "sampler_name": "res_multistep", "scheduler": "simple", "denoise": 1.0,
            "model": ["11", 0], "positive": ["27", 0], "negative": ["33", 0], "latent_image": ["13", 0]}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["3", 0], "vae": ["29", 0]}},
        "9": {"class_type": "SaveImage", "inputs": {"filename_prefix": "ai-video-mcp", "images": ["8", 0]}},
    }
    reference = args.get("storyboardReferenceBase64")
    if reference:
        os.makedirs(COMFYUI_INPUT_DIR, exist_ok=True)
        filename = f"mcp-storyboard-{uuid.uuid4().hex}.png"
        with open(os.path.join(COMFYUI_INPUT_DIR, filename), "wb") as handle:
            handle.write(base64.b64decode(reference))
        graph["40"] = {"class_type": "LoadImage", "inputs": {"image": filename}}
        graph["41"] = {"class_type": "ImageScale", "inputs": {
            "image": ["40", 0], "upscale_method": "lanczos", "width": int(args.get("width", 1024)),
            "height": int(args.get("height", 1024)), "crop": "center"}}
        graph["42"] = {"class_type": "VAEEncode", "inputs": {"pixels": ["41", 0], "vae": ["29", 0]}}
        graph["3"]["inputs"]["latent_image"] = ["42", 0]
        graph["3"]["inputs"]["denoise"] = float(args.get("storyboardStrength", 0.72))
    return graph


def generate_image(args):
    client_id = str(uuid.uuid4())
    queued = http_json(f"{COMFYUI_URL}/prompt", "POST", {"prompt": workflow(args), "client_id": client_id})
    prompt_id = queued["prompt_id"]
    deadline = time.time() + TIMEOUT
    while time.time() < deadline:
        history = http_json(f"{COMFYUI_URL}/history/{prompt_id}")
        item = history.get(prompt_id)
        if item:
            images = item.get("outputs", {}).get("9", {}).get("images", [])
            if not images:
                raise RuntimeError("ComfyUI completed without an image")
            image = images[0]
            query = urllib.parse.urlencode({
                "filename": image["filename"], "subfolder": image.get("subfolder", ""), "type": image.get("type", "output")})
            with urllib.request.urlopen(f"{COMFYUI_URL}/view?{query}", timeout=TIMEOUT) as response:
                data = response.read()
                mime = response.headers.get_content_type() or "image/png"
            return {"content": [{"type": "image", "mimeType": mime,
                                  "data": base64.b64encode(data).decode()}], "isError": False}
        time.sleep(1)
    raise TimeoutError(f"ComfyUI timeout after {TIMEOUT}s")


class McpHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        try:
            message = json.loads(self._read_body() or b"{}")
            method = message.get("method")
            print(f"[MCP] method={method}", flush=True)
            if method == "notifications/initialized":
                self.send_response(202)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            if method == "initialize":
                result = {"protocolVersion": "2025-06-18", "capabilities": {"tools": {}},
                          "serverInfo": {"name": "comfyui-image-mcp", "version": "1.0.0"}}
                self.reply(message.get("id"), result, session=True)
            elif method == "tools/list":
                self.reply(message.get("id"), {"tools": [{"name": "generate_image",
                    "description": "Generate one image with local ComfyUI", "inputSchema": {"type": "object",
                    "properties": {"prompt": {"type": "string"}, "width": {"type": "integer"},
                    "height": {"type": "integer"}}, "required": ["prompt"]}}]})
            elif method == "tools/call":
                params = message.get("params", {})
                if params.get("name") != "generate_image":
                    raise ValueError("Unknown tool: " + str(params.get("name")))
                self.reply(message.get("id"), generate_image(params.get("arguments", {})))
            else:
                self.error(message.get("id"), -32601, "Method not found")
        except Exception as exc:
            print(f"[MCP] error={exc!r}", flush=True)
            self.error(message.get("id") if "message" in locals() else None, -32000, str(exc))

    def _read_body(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size_line = self.rfile.readline().strip().split(b";", 1)[0]
                size = int(size_line, 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length)

    def reply(self, request_id, result, session=False):
        payload = json.dumps({"jsonrpc": "2.0", "id": request_id, "result": result}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        if session:
            self.send_header("Mcp-Session-Id", str(uuid.uuid4()))
        self.end_headers()
        self.wfile.write(payload)

    def error(self, request_id, code, text):
        payload = json.dumps({"jsonrpc": "2.0", "id": request_id,
                              "error": {"code": code, "message": text}}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt, *args):
        print("[MCP] " + fmt % args)


if __name__ == "__main__":
    print(f"MCP image server: http://{HOST}:{PORT}/mcp")
    print(f"ComfyUI backend: {COMFYUI_URL} | model: {UNET_MODEL}")
    ThreadingHTTPServer((HOST, PORT), McpHandler).serve_forever()
