/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.grip.hosted;

import com.sun.max.program.*;
import com.sun.max.vm.grip.*;

/**
 * @author Bernd Mathiske
 */
public class HostedGrip extends Grip {

    private final Object object;

    public HostedGrip(Object object) {
        this.object = object;
    }

    @Override
    public boolean equals(Object other) {
        if (other == object) {
            ProgramWarning.message("prototype grip bypassed");
            return true;
        }
        if (other instanceof HostedGrip) {
            final HostedGrip prototypeGrip = (HostedGrip) other;
            return prototypeGrip.object == object;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return object.hashCode();
    }

    public Object getObject() {
        return object;
    }
}