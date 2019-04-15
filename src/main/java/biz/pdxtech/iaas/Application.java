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

package biz.pdxtech.iaas;

import biz.pdxtech.iaas.entity.Address;
import biz.pdxtech.iaas.hazelcast.CacheData;
import biz.pdxtech.iaas.proto.StreamInfoModel;
import biz.pdxtech.iaas.repository.AddressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;
import java.util.Date;

@EntityScan(basePackages = {"biz.pdxtech.iaas.entity"})
@EnableScheduling
@EnableRetry
@SpringBootApplication
public class Application {

    //缓存配置
    @Value("${hazelcast.name}")
    private String hazelcastName;
    @Value("${hazelcast.password}")
    private String hazelcastPassword;
    @Value("${hazelcast.address}")
    private String hazelcastAddress;
//    @Value("${json.rpc}")
//    private String rpc;
    @Value("${pdx.rece.account}")
    private String account;

    @Value("${pdx.iaas.baapStreamServiceHost}")
    private String baapStreamServiceHost;
    @Value("${pdx.iaas.baapStreamAccount}")
    private String baapStreamAccount;
    @Value("${pdx.iaas.baapStreamPrice}")
    private long baapStreamPrice;

//    public static String jsonRpc = null;
    public static String rece_Account = null;


    public static String IAAS_ADDRESS, IAAS_PRIVATE_KEY, BASIC_SERVICE_CHAIN_ADDRESS, BASIC_SERVICE_CHAIN_PRIVATE_KEY;
    @Value("${pdx.iaas.address}")
    private String iaasAddress;
    @Value("${pdx.iaas.private-key}")
    private String iaasPrivateKey;
    @Value("${pdx.iaas.basic-service-chain.address}")
    private String basicServiceChainAddress;
    @Value("${pdx.iaas.basic-service-chain.private-key}")
    private String basicServiceChainPrivateKey;


    @Autowired
    private AddressRepository addressRepository;

    @PostConstruct
    private void init() {
        //设置缓存信息
        System.setProperty(CacheData.HAZELCASTNAME, hazelcastName);
        System.setProperty(CacheData.HAZELCASTPWD, hazelcastPassword);
        System.setProperty(CacheData.HAZELCASTADDRESS, hazelcastAddress);
//        jsonRpc = rpc;
        rece_Account = account;

        IAAS_ADDRESS = iaasAddress;
        IAAS_PRIVATE_KEY = iaasPrivateKey;
        BASIC_SERVICE_CHAIN_ADDRESS = basicServiceChainAddress;
        BASIC_SERVICE_CHAIN_PRIVATE_KEY = basicServiceChainPrivateKey;
        //save iaas & service-chain address
        Address address1 = addressRepository.findAddressByAddr(iaasAddress);
        if (address1 == null) {
            addressRepository.save(Address.builder().addr(iaasAddress).createdAt(new Date()).build());
        }
        Address address2 = addressRepository.findAddressByAddr(basicServiceChainAddress);
        if (address2 == null) {
            addressRepository.save(Address.builder().addr(basicServiceChainAddress).createdAt(new Date()).build());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public StreamInfoModel getStreamInfoModel() {
        return StreamInfoModel.builder().baapStreamAccount(baapStreamAccount).baapStreamServiceHost(baapStreamServiceHost).baapStreamPrice(baapStreamPrice).build();
    }

}
