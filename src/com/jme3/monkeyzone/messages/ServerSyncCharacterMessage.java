/*
 * Copyright (c) 2009-2011 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.monkeyzone.messages;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.network.message.Message;
import com.jme3.network.serializing.Serializable;

/**
 * Sync message for character objects
 * @author normenhansen
 */
@Serializable()
public class ServerSyncCharacterMessage extends Message {

    public long id;
    public Vector3f location = new Vector3f();
    //TODO: rotation is not used in character, sync character spatial rotation instead?
    public Matrix3f rotation = new Matrix3f();
    public Vector3f walkDirection = new Vector3f();

    public ServerSyncCharacterMessage() {
    }

    public ServerSyncCharacterMessage(long id, CharacterControl character) {
        setReliable(false);
        this.id = id;
        character.getPhysicsLocation(location);
        character.getPhysicsRotation(rotation);
        this.walkDirection.set(character.getWalkDirection());
    }

    public void readData(CharacterControl character) {
        character.getPhysicsLocation(location);
        character.getPhysicsRotation(rotation);
        this.walkDirection.set(character.getWalkDirection());
    }

    public void applyData(CharacterControl character) {
        character.setPhysicsLocation(location);
        character.setPhysicsRotation(rotation);
        character.setWalkDirection(walkDirection);
    }
}
