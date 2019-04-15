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

import biz.pdxtech.baap.api.Constants;
import biz.pdxtech.baap.driver.BlockChainDriverFactory;
import biz.pdxtech.baap.driver.BlockchainDriverException;
import biz.pdxtech.baap.driver.ethereum.EthereumBlockchainDriver;

import java.util.Properties;

public class BaapDriverUtil {

    public static BaapDriverUtil getInstance(){
        return new BaapDriverUtil();
    }

    private static EthereumBlockchainDriver driver;

    public static EthereumBlockchainDriver getDefault(String jsonRpc, String chainId) {
        Properties properties = new Properties();
        properties.setProperty(Constants.BAAP_ENGINE_TYPE_KEY, Constants.BAAP_ENGINE_TYPE_PDX);
        properties.setProperty(Constants.BAAP_ENGINE_URL_HOST_KEY, jsonRpc);
        properties.setProperty(Constants.BAAP_SENDER_PRIVATE_KEY, "4a4859b1e598550862068d8a17752b4c145f2d6f44dc7935f6a51b717e4bc417");
        properties.setProperty(Constants.BAAP_ENGINE_ID, chainId);

        // driver
        try {
            if (driver == null) {
                driver = (EthereumBlockchainDriver) BlockChainDriverFactory.get(properties);
            }
            return driver;
        } catch (BlockchainDriverException e) {
            e.printStackTrace();
        }
        return null;
    }


    public EthereumBlockchainDriver getDriver(String jsonRpc, String chainId){
        Properties properties = new Properties();
        properties.setProperty(Constants.BAAP_ENGINE_TYPE_KEY, Constants.BAAP_ENGINE_TYPE_PDX);
        properties.setProperty(Constants.BAAP_ENGINE_URL_HOST_KEY, jsonRpc);
        properties.setProperty(Constants.BAAP_SENDER_PRIVATE_KEY, "4a4859b1e598550862068d8a17752b4c145f2d6f44dc7935f6a51b717e4bc417");
        properties.setProperty(Constants.BAAP_ENGINE_ID, chainId);


        try {
            EthereumBlockchainDriver driver = (EthereumBlockchainDriver) BlockChainDriverFactory.get(properties);
            return driver;
        } catch (BlockchainDriverException e) {
            e.printStackTrace();
        }
        return null;

    }
}
