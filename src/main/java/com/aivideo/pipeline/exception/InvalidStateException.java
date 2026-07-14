package com.aivideo.pipeline.exception;

/** Ném khi thao tác không hợp lệ với trạng thái hiện tại của job,
 *  vd: duyệt kịch bản khi job chưa ở SCRIPT_READY. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}
