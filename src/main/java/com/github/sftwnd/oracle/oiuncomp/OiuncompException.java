package com.github.sftwnd.oracle.oiuncomp;

import java.io.IOException;

/**
 * The data does not conform to the compression format of Oracle Installer: no header, an invalid code width, a code
 * that a valid stream cannot contain or a truncated code.
 */
public class OiuncompException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a description of the format violation.
     *
     * @param message the description of the violation
     */
    public OiuncompException(String message) {
        super(message);
    }
}
