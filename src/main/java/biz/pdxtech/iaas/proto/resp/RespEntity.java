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

package biz.pdxtech.iaas.proto.resp;


public class RespEntity {

    private static final String SUCCESS = "success";

    public int code;
    public String msg;
    public Object data;

    public RespEntity(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public RespEntity(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static RespEntity success() {
        return new RespEntity(200, SUCCESS);
    }

    public static RespEntity success(String objStr) {
        return new RespEntity(200, SUCCESS, objStr);
    }

    public static RespEntity success(Object obj) {
        return new RespEntity(200, SUCCESS, obj);
    }

    public static RespEntity error(RespCode respCode) {

        return new RespEntity(respCode.getErrorCode(), respCode.getErrorMsg());
    }

}
