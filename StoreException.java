package ai;

class StoreException extends Exception {
    StoreException(String msg) { super(msg); }
    StoreException(String msg, Throwable cause) { super(msg, cause); }
}
