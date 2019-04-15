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

package biz.pdxtech.iaas.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.DatatypeConverter;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Properties;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AuthToken {

    String addr;
    byte[] salt;
    long timestamp;

    Properties meta;

    public String sign(PrivateKey priK) throws JsonProcessingException, Exception {
        byte[] data = new ObjectMapper().writeValueAsBytes(this);
        byte[] sig = CryptoUtil.sign(priK, data);
        return DatatypeConverter.printHexBinary(data) + "." + DatatypeConverter.printHexBinary(sig);
    }

    public static AuthToken verify(PublicKey pubK, String sig) throws Exception {
        String[] items = sig.split("\\.");
        if (items.length != 2) return null;

        byte[] data = DatatypeConverter.parseHexBinary(items[0]);
        byte[] sign = DatatypeConverter.parseHexBinary(items[1]);

        if (!CryptoUtil.verify(pubK, sign, data)) return null;

        return new ObjectMapper().readValue(data, AuthToken.class);
    }
}
