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

import java.io.StringWriter;
import java.util.stream.IntStream;

public class PasswordUtil {
    //根据指定的规则生成新的密码
    /*public static String getPassword(String,){

    }*/

    public static String addSoltTypeOne(String password, String uid) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("*").append(getSubStringUid(uid)).append(password).append("PDX").append("#");
        //System.out.println (buffer.toString ());
        String encryption32 = MD5Util.Encryption_32(buffer.toString());
        //System.out.println (encryption32);
        return encryption32;
    }

    //获取uid的指定位数的字符组成的字符串
    private static String getSubStringUid(String uid) {
        String s = IntStream.range(0, uid.length()).filter(index -> index == 2 ||
                index == 3 || index == 6).map(index -> uid.charAt(index)).collect(
                StringWriter::new,
                StringWriter::write,
                (swl, swr) -> swl.write(swr.toString()))
                .toString();
        return s;
    }

    public static void main(String[] args) {
        addSoltTypeOne("12343566", "dhfdusbbccmm");

    }
}
