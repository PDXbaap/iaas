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

package biz.pdxtech.iaas.util;

import java.security.MessageDigest;

public class MD5Util {

    private final static long UID_PARAM = 4793251;

    /**
     * MD5加密(16位)
     */
    public static String Encryption_16(String plainText) {
        try {
            return Encryption_32(plainText).substring(8, 24);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * MD5加密(32位)
     */
    public static String Encryption_32(String plainText) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(plainText.getBytes());
            byte[] bytes = md5.digest();
            StringBuffer buffer = new StringBuffer();
            for (int i = 0; i < bytes.length; ++i) {
                int val = bytes[i];
                if (val < 0) {
                    val += 256;
                }
                if (val < 16) {
                    buffer.append("0");
                }
                buffer.append(Integer.toHexString(val));
            }
            return buffer.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

}
