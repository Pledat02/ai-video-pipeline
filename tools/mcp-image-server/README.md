# Local MCP Image Server

Cầu nối MCP Streamable HTTP giữa AI Video Pipeline và ComfyUI.

## Chạy

1. Khởi động ComfyUI tại `http://127.0.0.1:8188`.
2. MCP bridge mặc định dùng bộ Z-Image Turbo do ComfyUI Desktop cài:

```powershell
python tools/mcp-image-server/server.py
```

3. Trong `.env` của pipeline:

```env
MCP_IMAGE_SERVER_URL=http://127.0.0.1:8189/mcp
MCP_IMAGE_TOOL=generate_image
```

4. Trên dashboard chọn `Local MCP · ComfyUI / Custom Server`.

Server chỉ bind vào `127.0.0.1` theo mặc định và không cần package Python bên ngoài.

Có thể đổi model bằng `COMFYUI_UNET_MODEL`, `COMFYUI_CLIP_MODEL` và `COMFYUI_VAE_MODEL`.
