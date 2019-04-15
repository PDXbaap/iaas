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

package biz.pdxtech.iaas.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import java.util.concurrent.TimeUnit;


public class CacheData {

    public static final String HAZELCASTNAME = "hazelcast.name";
    public static final String HAZELCASTPWD = "hazelcast.password";
    public static final String HAZELCASTADDRESS = "hazelcast.address";

    private final String CACHEDATA = "CACHEDATA";

    private static CacheData cacheData = null;
    private HazelcastInstance client = null;

    private CacheData() {
        client = getClient();
    }

    public static CacheData getInstance() {
        if (cacheData == null) {
            cacheData = new CacheData();
        }
        return cacheData;
    }

    private HazelcastInstance getClient() {
        ClientConfig clientConfig = new ClientConfig();
        GroupConfig gconf = new GroupConfig();
        gconf.setName(System.getProperty(HAZELCASTNAME));
        gconf.setPassword(System.getProperty(HAZELCASTPWD));
        clientConfig.setGroupConfig(gconf);
        clientConfig.getNetworkConfig().addAddress(System.getProperty(HAZELCASTADDRESS));
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        return client;
    }

    /**
     * 存储查询数据
     *
     * @return
     */
    public IMap<String, Object> getCacheData() {
        return this.client.getMap(CACHEDATA);
    }


}
