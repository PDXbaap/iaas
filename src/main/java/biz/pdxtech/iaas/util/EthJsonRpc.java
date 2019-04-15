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

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class EthJsonRpc {

    public static final String DECIMALS = "{\"constant\": true,\"inputs\": [],\"name\": \"decimals\",\"outputs\": [{\"name\": \"\",\"type\": \"uint8\"}],\"payable\": false,\"stateMutability\": \"view\",\"type\": \"function\"}";
    public static final String TOTALSUPPLY = "{\"constant\": true,\"inputs\": [],\"name\": \"totalSupply\",\"outputs\": [{\"name\": \"\",\"type\": \"uint256\"}],\"payable\": false,\"stateMutability\": \"view\",\"type\": \"function\"}";
    public static final String NAME = "{\"constant\": true,\"inputs\": [],\"name\": \"name\",\"outputs\": [{\"name\": \"\",\"type\": \"string\"}],\"payable\": false,\"stateMutability\": \"view\",\"type\": \"function\"}";
    public static final String SYMBOL = "{\"constant\": true,\"inputs\": [],\"name\": \"symbol\",\"outputs\": [{\"name\": \"\",\"type\": \"string\"}],\"payable\": false,\"stateMutability\": \"view\",\"type\": \"function\"}";
    public static final String BALANCEOF = "{\"constant\": true,\"inputs\": [{\"name\": \"_owner\",\"type\": \"address\"}],\"name\": \"balanceOf\",\"outputs\": [{\"name\": \"\",\"type\": \"uint256\"}],\"payable\": false,\"stateMutability\": \"view\",\"type\": \"function\"}";

    private JsonRpcHttpClient client;

    public EthJsonRpc(String url) throws MalformedURLException {
        client = new JsonRpcHttpClient(new URL(url));
        client.setContentType("application/json");
    }

    public String getBalance(String addr) {
        String method = "eth_getBalance";
        try {
            String result = client.invoke(method, new Object[]{addr, "latest"}, String.class);
            return result;
        } catch (Throwable e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public String getNonce(String addr) {
        String method = "eth_getTransactionCount";
        try {
            String result = client.invoke(method, new Object[]{addr, "latest"}, String.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public String blockNumber() {
        String method = "eth_blockNumber";
        try {
            String result = client.invoke(method, new Object[]{}, String.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public String blockCommitNumber() {
        String method = "eth_blockCommitNumber";
        try {
            String result = client.invoke(method, new Object[]{}, String.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public String sendRawTransaction(String data) {
        String method = "eth_sendRawTransaction";
        try {
            String result = client.invoke(method, new Object[]{data}, String.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public String call(String to, String data) {
        String method = "eth_call";
        try {
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("to", to);
            args.put("data", data);
            String result = client.invoke(method, new Object[]{args, "latest"}, String.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public String callParam(Map<String, Object> args) {
        String method = "eth_call";
        try {
            String result = client.invoke(method, new Object[]{args, "latest"}, String.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Object> getBlockByNumber(String blockNumber) {
        String method = "eth_getBlockByNumber";
        try {
            Map<String, Object> result = client.invoke(method, new Object[]{blockNumber, true}, Map.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, Object> getWithdrawTransaction(String hash) {
        String method = "eth_getWithDrawTransactionByHash";
        try {
            Map<String, Object> result = client.invoke(method, new Object[]{hash}, Map.class);
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

}
