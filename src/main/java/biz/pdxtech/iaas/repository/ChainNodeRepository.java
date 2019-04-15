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
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.entity.Node;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import javax.persistence.LockModeType;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.List;


@Repository
public interface ChainNodeRepository extends CrudRepository<Chain_Node, Long> {


    List<Chain_Node> findChain_NodesByChainAndStatIsNot(Chain chain, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByChain(Chain chain);

    List<Chain_Node> findChain_NodesByChain_ChainIdAndStatIsNot(Long chainId, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByChain_ChainIdAndNode_IpIsNotAndStatIsNot(Long chainId, String ip, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByChainAndStatIs(Chain chain, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByNode_IdAndStatIsNot(Long nodeId, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByChainAndStat(Chain chain, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByChain_IdAndStat(Long chainId, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByNodeAndStatIsNot(Node node, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByChainAndStatIsNotOrderByIdDesc(Chain chain, Chain_Node.Stat stat);

    List<Chain_Node> findAllByNode_Id(Long nodeId);

    List<Chain_Node> findChain_NodesByChainInAndStatIsNot(List<Chain> chains, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByNode_IdInAndChain_TypeIsAndStatIsNot(List<Long> nodeId, Chain.Type type, Chain_Node.Stat stat);

    List<Chain_Node> findChain_NodesByChain_TypeIsAndStatIsNot( Chain.Type type, Chain_Node.Stat stat);

    List<Chain_Node> findAllByNode_Addr_AddrAndStatIsNot(String nodeAddress, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByChain_ChainIdAndNode_IdAndStatIs(Long chainId, Long nodeId, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByChain_ChainIdAndNode_IdAndStatIsNot(Long chainId, Long nodeId, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByChain_ChainIdAndNode_IpAndStatIs(Long chainId, String nodeIP, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByChainAndNodeAndStatIsNot(Chain chain, Node node, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByChainAndNodeAndStatIs(Chain chain, Node node, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByChainAndNodeAndStatIsAndEvidencedIs(Chain chain, Node node, Chain_Node.Stat stat, boolean bool);

    Chain_Node findChain_NodeByChainIdAndNodeIdAndStatIsNot(long chainId, long nodeId, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByNodeAndStatIsNotAndChain_Type(Node node, Chain_Node.Stat stat, Chain.Type chainType);

    Chain_Node findChain_NodeByChain_ChainIdAndNode_IpAndStatIsNot(long chainId, String ip, Chain_Node.Stat stat);

    Chain_Node findChain_NodeByChain_ChainIdAndNode_Addr_AddrAndStatIsNot(long chainId, String address, Chain_Node.Stat stat);

    Chain_Node findChain_NodeById(Long id);

    long countByChainAndStatIsNot(Chain chain, Chain_Node.Stat stat);

    @Query(value = "select node_id from (select count(*)as size,`node_id` from chain_node where stat <> 2  group by `node_id`) as t where size < ?", nativeQuery = true)
    List<BigInteger> findAllByNodeSize(int nodeSize);

}
