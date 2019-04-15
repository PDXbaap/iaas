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

package biz.pdxtech.iaas.repository;


import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.ChainOrder;
import biz.pdxtech.iaas.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChainOrderRepository extends CrudRepository<ChainOrder, Long> {

    Page<ChainOrder> findByChain_StatAndUserOrderByCreateTimeDesc(Chain.Stat stat, User user, Pageable pageable);

    Page<ChainOrder> findByUserOrderByCreateTimeDesc(User user, Pageable pageable);

    ChainOrder findById(long id);

    ChainOrder findByIdAndUserId(long id, long user_id);

    ChainOrder findByUuidAndUserId(String uid, long user_id);

    ChainOrder findByIdAndType(long id, int orderType);

    ChainOrder findByChainNameAndUserId(String name, long user_id);

    ChainOrder findByChainName(String name);

    ChainOrder findByFromAddr(String addr);

    ChainOrder findChainOrderByFromAddrAndStatIs(String address, ChainOrder.Stat stat);

    List<ChainOrder> findByStatAndUserId(ChainOrder.Stat stat, long user_id);

}
