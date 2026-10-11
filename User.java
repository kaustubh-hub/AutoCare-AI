package ai;

import java.io.Serializable;

class User implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    int id;
    String username, phone, passwordHash;
    User copy() { try { return (User) clone(); } catch (CloneNotSupportedException e) { throw new IllegalStateException(e); } }
}
