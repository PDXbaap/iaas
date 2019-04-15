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

package biz.pdxtech.iaas.proto;

import biz.pdxtech.iaas.proto.resp.RespCode;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import java.util.Map;


@Data
@Log4j2
@ToString
public class RespEntity {
    public static final Integer SUCCESSCODE = 200;
    public int status;
    public Object attachment;
    Map<String, String> meta;

    public RespEntity(int status, Object attachment) {
        this.status = status;
        this.attachment = attachment;
    }

    public RespEntity(int status, Object attachment, Map<String, String> meta) {
        this.attachment = attachment;
        this.status = status;
        this.meta = meta;
    }

    public RespEntity(int status, Map<String, String> meta) {
        this.meta = meta;
        this.status = status;
    }

    public static RespEntity success(Object object) {
        return new RespEntity(SUCCESSCODE, object);
    }

    public static RespEntity error(RespCode respCode) {
        return new RespEntity(respCode.getErrorCode(), respCode.getErrorMsg());
    }

    public static RespEntity error(RespCode respCode, Map<String, String> extend) {
        return new RespEntity(respCode.getErrorCode(), respCode.getErrorMsg(), extend);
    }

}