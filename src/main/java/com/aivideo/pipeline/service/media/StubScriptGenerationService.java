package com.aivideo.pipeline.service.media;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stub tạm để pipeline chạy end-to-end ngay từ ngày đầu.
 * Mặc định khi pipeline.script-generation.provider chưa set hoặc = stub.
 */
@Service
@ConditionalOnProperty(prefix = "pipeline.script-generation", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubScriptGenerationService implements ScriptGenerationService {

    @Override
    public String generateScript(String topic) {
        return """
                [KỊCH BẢN NHÁP - sinh bởi stub, tuần 3 sẽ thay bằng LLM thật]

                Chủ đề: %s

                Ngày xửa ngày xưa... (nội dung kịch bản sẽ được LLM sinh ra ở đây,
                sau đó BẠN duyệt và chỉnh sửa trước khi cho render).
                """.formatted(topic);
    }
}
