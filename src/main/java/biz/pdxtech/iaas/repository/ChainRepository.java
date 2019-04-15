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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;


@Repository
public interface ChainRepository extends CrudRepository<Chain, Long> {

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    Chain findChainById(Long id);

    Chain findChainByName(String name);

    Chain findChainByChainId(Long chainId);

    Chain findChainByChainIdAndStatIsNot(Long chainId, Chain.Stat stat);

    Chain findChainByNameAndStatIsNot(String name, Chain.Stat stat);

    Chain findChainByNameAndType(String name, Chain.Type type);

    List<Chain> findChainsByTypeIsAndStatIsNot(Chain.Type type, Chain.Stat stat);

    List<Chain> findChainsByTypeIsNotAndStatIsNot(Chain.Type type, Chain.Stat stat);

    List<Chain> findChainsByTypeIsNotAndStatIs(Chain.Type type, Chain.Stat stat);

    List<Chain> findChainsByTypeIsAndStatIs(Chain.Type type, Chain.Stat stat);

    List<Chain> findChainsByLowerChainIdIsAndStatIsNot(Long chainId, Chain.Stat stat);

    List<Chain> findChainsByTypeIsAndStatIsAndShareChainId(Chain.Type type, Chain.Stat stat, Long shareChainId);

    List<Chain> findChainsByTypeIsNotAndStatIsAndShareChainId(Chain.Type type, Chain.Stat stat, Long shareChainId);

    List<Chain> findChainsByTypeAndStatIsNotOrderByIdDesc(Chain.Type type, Chain.Stat stat);

    List<Chain> findChainsByLowerChainIdAndAndTypeIs(Long chainId, Chain.Type type);

    List<Chain> findChainsByLowerChainIdAndTypeIsNot(long id, Chain.Type type);

    List<Chain> findChainsByLowerChainIdAndStatIsNot(Long chainId, Chain.Stat stat);

    List<Chain> findChainsByPartBetweenAndStatIsNot(Long start, Long end, Chain.Stat stat);

    Page<Chain> findChainsByTypeAndStatIsNotAndSizeServiceGreaterThanOrderByIdDesc(Chain.Type type, Chain.Stat stat, int sizeService, Pageable page);

    Page<Chain> findChainsByTypeAndStatIsNot(Chain.Type type, Chain.Stat stat, Pageable page);

    @Query(value = "select count(size_desired) from chain where `type` = 2", nativeQuery = true)
    int getTotalDesireNodeNum();

    @Query(value = "select count(size_service) from chain where `type` = 0", nativeQuery = true)
    int getTotalServiceNodeNum();


}
