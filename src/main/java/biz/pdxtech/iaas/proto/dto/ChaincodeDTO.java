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

package biz.pdxtech.iaas.proto.dto;

import javax.validation.constraints.NotNull;

public class ChaincodeDTO {
    @NotNull
    private String payTxSign;
    @NotNull
    private String streamHashSign;
    private String fileHash;
    private String streamHash;
    @NotNull
    private String pbk;
    @NotNull
    private String streamId;

    @NotNull
    private String channel;
    private String fileName;
    @NotNull
    private String chaincode;
    @NotNull
    private String version;
    private String alias;
    private String desc;

    public String getPayTxSign() {
        return payTxSign;
    }

    public void setPayTxSign(String payTxSign) {
        this.payTxSign = payTxSign;
    }

    public String getStreamHashSign() {
        return streamHashSign;
    }

    public void setStreamHashSign(String streamHashSign) {
        if (streamHashSign.startsWith("0x")) {
            this.streamHashSign = streamHashSign.substring(2);
        } else {
            this.streamHashSign = streamHashSign;
        }
    }

    public String getPbk() {
        return pbk;
    }

    public void setPbk(String pbk) {
        this.pbk = pbk;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName.replaceAll("[\\u4e00-\\u9fa5[\\u3002\\uff1b\\uff0c\\uff1a\\u201c\\u201d\\uff08\\uff09\\u3001\\uff1f\\u300a\\u300b]]", "");
    }

    public String getChaincode() {
        return chaincode;
    }

    public void setChaincode(String chaincode) {
        this.chaincode = chaincode;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getStreamHash() {
        return streamHash;
    }

    public void setStreamHash(String streamHash) {
        if (streamHash.startsWith("0x")) {
            this.streamHash = streamHash.substring(2);
        } else {
            this.streamHash = streamHash;
        }
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        if (fileHash.startsWith("0x")) {
            this.fileHash = fileHash.substring(2);
        } else {
            this.fileHash = fileHash;
        }
    }

    @Override
    public String toString() {
        return "ChaincodeDTO{" +
                "payTxSign='" + payTxSign + '\'' +
                ", streamHashSign='" + streamHashSign + '\'' +
                ", pbk='" + pbk + '\'' +
                ", streamId='" + streamId + '\'' +
                ", channel='" + channel + '\'' +
                ", fileName='" + fileName + '\'' +
                ", chaincode='" + chaincode + '\'' +
                ", version='" + version + '\'' +
                ", alias='" + alias + '\'' +
                ", desc='" + desc + '\'' +
                '}';
    }
}
