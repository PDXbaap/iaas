/*************************************************************************
 * Copyright (C) 2016-2019 PDX Technologies, Inc. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *************************************************************************/

package biz.pdxtech.iaas.common;

public enum DeployStatus {

    READY("ready", (byte) 1), STREAMHANDLING("streamHandling", (byte) 2), DEPLOYABLE("deployable", (byte) 3), RUNNING("running", (byte) 4), STOP("stop", (byte) 5), ERROR("error", (byte) 6);
    private String name;
    private byte value;

    DeployStatus(String name, byte value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public byte getValue() {
        return value;
    }
}
