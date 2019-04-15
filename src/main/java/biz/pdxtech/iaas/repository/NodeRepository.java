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

import biz.pdxtech.iaas.entity.Node;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface NodeRepository extends CrudRepository<Node, Long> {

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    Node findNodeById(Long id);

    @Query(value = "select * from node where id = :id for update", nativeQuery = true)
    Node findNodeByIdForUpdate(@Param("id") long id);

    Node findNodeByIpAndStatIsNot(String ip, Node.Stat stat);

    Node findByOwner_Addr(String node);

    Node findNodeByAddr_Addr(String address);

    List<Node> findNodesByStatIsNotAndSizeServiceLessThanEqual(Node.Stat stat, int sizeService);

    List<Node> findByPartBetweenOrderBySizeServiceDesc(long max, long min);

    List<Node> findNodesByStatIsNotOrderBySizeServiceAsc(Node.Stat stat);

    List<Node> findNodesByStatIsNot(Node.Stat stat);

    List<Node> findNodesByStatIsAndSizeServiceLessThanEqual(Node.Stat stat, int sizeService);

    List<Node> findNodesByStatIs(Node.Stat stat);

}