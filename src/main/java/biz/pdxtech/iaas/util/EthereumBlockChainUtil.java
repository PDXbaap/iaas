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

import biz.pdxtech.baap.util.http.BaapHttpClient;
import biz.pdxtech.baap.util.json.BaapJSONUtil;
import okhttp3.Response;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EthereumBlockChainUtil {

    private static BaapHttpClient client = new BaapHttpClient();

    public static Response callStack(String sign, String jsonRpc) throws IOException {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("jsonrpc", "2.0");
        requestData.put("id", 1);
        requestData.put("params", new String[]{sign});
        requestData.put("method", "eth_sendRawTransaction");
        return client.call(
                "application/json",
                jsonRpc,
                BaapJSONUtil.toJson(requestData).getBytes()
        );
    }
}
