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

package biz.pdxtech.iaas.dao;

import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.repository.ChainRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ChainDao {

    private final ChainRepository chainRepository;

    @Autowired
    public ChainDao(ChainRepository chainRepository){
        this.chainRepository = chainRepository;
    }

    /**
     * 更新链节点数量
     *
     * @param chain 链
     */
    @Transactional(rollbackFor = Exception.class)
    public Chain updateSizeService(Chain chain, int number) {
        chain = chainRepository.findChainById(chain.getId());
        chain.setSizeService(chain.getSizeService() + number);
        return chainRepository.save(chain);
    }

    /**
     * 更新链服务数量
     *
     * @param chain 链
     */
    @Transactional(rollbackFor = Exception.class)
    public Chain updateSizeChain(Chain chain, int number) {
        chain = chainRepository.findChainById(chain.getId());
        chain.setSizeChain(chain.getSizeChain() + number);
        return chainRepository.save(chain);
    }

}
