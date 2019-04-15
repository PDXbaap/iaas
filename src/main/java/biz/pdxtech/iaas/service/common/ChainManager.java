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

package biz.pdxtech.iaas.service.common;

import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.proto.dto.GenesisParamDTO;
import biz.pdxtech.iaas.repository.ChainNodeRepository;
import biz.pdxtech.iaas.repository.ChainRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChainManager {

    @Value("${pdx.iaas.address}")
    private String iaasAddress;
    @Value("${pdx.iaas.basic-service-chain.name}")
    private String basicServiceChainName;
    @Value("${pdx.iaas.basic-service-chain.token}")
    private String basicServiceChainToken;
    @Value("${pdx.iaas.basic-service-chain.address}")
    private String basicServiceChainAddress;
    @Value("${pdx.iaas.transfer.token.limit}")
    private String transferTokenLimit;
    @Value("${pdx.iaas.transfer.token.amount}")
    private String transferTokenAmount;


    @Autowired
    private ChainNodeRepository chainNodeRepository;
    @Autowired
    private ChainRepository chainRepository;


    /**
     * 获取一个活跃节点
     *
     * @param chainNodes 链节点列表
     * @return 活跃节点
     */
    public Chain_Node getActiveChainNode(List<Chain_Node> chainNodes) {
        Long chainId = null;
        if (!chainNodes.isEmpty()) {
            chainId = chainNodes.get(0).getChain().getChainId();
        }
        chainNodes = chainNodes.stream().filter(chainNode -> chainNode.getUpdatedAt() != null).filter(Chain_Node::isConnected).filter(Chain_Node::isEvidenced).collect(Collectors.toList());
        chainNodes.sort(Comparator.comparing(Chain_Node::getUpdatedAt).reversed());
        if (!chainNodes.isEmpty()) {
            log.info("Got active chain node success >> chainId:{}, nodeIP:{} ", chainId, chainNodes.get(0).getNode().getIp());
            return chainNodes.get(0);
        } else {
            log.warn("Got active chain node failed >> chainId:{} ", chainId);
            return null;
        }
    }


    /**
     * 获取一个活跃节点
     *
     * @param chain 链
     * @return
     */
    public Chain_Node getActiveChainNode(Chain chain) {
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        chainNodes = chainNodes.stream().filter(chainNode -> chainNode.getUpdatedAt() != null).filter(Chain_Node::isConnected).filter(Chain_Node::isEvidenced).collect(Collectors.toList());
        chainNodes.sort(Comparator.comparing(Chain_Node::getUpdatedAt).reversed());

        if (!chainNodes.isEmpty()) {
            log.info("Got active chain node success >> chainId:{}, nodeIP:{} ", chain.getChainId(), chainNodes.get(0).getNode().getIp());
            return chainNodes.get(0);
        } else {
            log.warn("Got active chain node failed >> chainId:{} ", chain.getChainId());
            return null;
        }
    }


    /**
     * 获取活跃节点列表
     *
     * @param chain  链
     * @param number 数量
     * @return
     */
    public List<Chain_Node> getActiveChainNodes(Chain chain, int number) {
        List<Chain_Node> chainNodes = chainNodeRepository.findChain_NodesByChainAndStatIsNot(chain, Chain_Node.Stat.REMOVED);
        return getActiveChainNodes(chainNodes, number);
    }


    /**
     * 获取活跃节点列表
     *
     * @param chainNodes 链节点列表
     * @param number     数量
     * @return
     */
    public List<Chain_Node> getActiveChainNodes(List<Chain_Node> chainNodes, int number) {
        Long chainId = null;
        if (!chainNodes.isEmpty()) {
            chainId = chainNodes.get(0).getChain().getChainId();
        }
        chainNodes = chainNodes.stream()
                .filter(chainNode -> chainNode.getUpdatedAt() != null)
                .filter(Chain_Node::isConnected)
                .filter(Chain_Node::isEvidenced)
                .limit(number).collect(Collectors.toList());
        log.info("Got active chain nodes success >> chainId:{}, require amount:{}, actual amount:{} ", chainId, number, chainNodes.size());
        return chainNodes;
    }


    /**
     * 判断是否存在活跃节点
     *
     * @param chain 链
     * @return
     */
    public boolean existActiveNode(Chain chain) {
        Chain_Node activeChainNode = getActiveChainNode(chain);
        return Objects.nonNull(activeChainNode);
    }


    /**
     * 判断节点是否活跃
     *
     * @param chainNode 链节点
     * @return
     */
    public boolean isActive(Chain_Node chainNode){
        if (Objects.isNull(chainNode) || chainNode.getStat()!=Chain_Node.Stat.IN_SERVICE){
            return false;
        }
        return (chainNode.isConnected() && chainNode.isEvidenced());
    }


    private static String TEST_ACCOUNT = ",\"251b3740a02a1c5cf5ffcdf60d42ed2a8398ddc8\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "    \"1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "    \"0xf51dd0fa6b08e15b9edda06336a6198f52e8208d\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "    \"0x9b620503368dc49e1687359f1b0c18c730aa7fda\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "    \"0x0eb92bf527fee9d6aea7280070e507e30e5871b9\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "   \"0x9a7a9879e1f0075c5744568bba87a8761142137a\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "   \"0xc0925ede3d2ff6dd3f4041965f45bfc74957a23f\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "   \"0xcab6a09d25e3f83b5a514758b51741306747b609\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "   \"0x47e25b17a5eef0491cead57abdb702bd6f169e6f\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "   \"0x1fd1f57276385ce3213d10ec8c9b9883c5a23609\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "   \"0x8000d109daef5c81799bc01d4d82b0589deedb33\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }," +
            "   \"0xef16e5a8cb38ab546d37a194c7a403d5e16ee5fa\": {" +
            "      \"balance\": \"3000000000000000000000000000\"" +
            "    }";

    /**
     * 获取普通创世文件
     *
     * @param chainId 链id
     * @return
     */
    public String getCommonGenesisJson(String chainId, int cfd, int blockDelay) {
        String alloc = "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}" + TEST_ACCOUNT;
        //String alloc = "";//正式环境
        String tokenChain = ",\"noRewards\":false";
        GenesisParamDTO dto = GenesisParamDTO.builder()
                .chainId(chainId)
                .alloc(alloc)
                .cfd(cfd)
                .blockDelay(blockDelay)
                .tokenChain(tokenChain).build();
        return getGenesisJson(dto);
    }


    /**
     * 获取带TokenChain的创世文件
     *
     * @param chainId    链id
     * @param tokenChain 共享币
     * @return
     */
    public String getGenesisJsonWithTokenChain(String chainId, String tokenChain, int cfd, int blockDelay) {
        String alloc = "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}" + TEST_ACCOUNT;
        //String alloc = "";//正式环境
        tokenChain = ",\"noRewards\":true,\"tokenChain\":" + tokenChain;
        GenesisParamDTO dto = GenesisParamDTO.builder()
                .chainId(chainId)
                .alloc(alloc)
                .cfd(cfd)
                .blockDelay(blockDelay)
                .tokenChain(tokenChain).build();
        return getGenesisJson(dto);
    }


    /**
     * 获取基础服务链的创世文件
     *
     * @param chainId 链id
     * @return
     */
    public String getBasicServiceChainGenesisJson(String chainId, int cfd, int blockDelay) {
        String alloc = "\"" + iaasAddress + "\":{\"balance\":\"" + basicServiceChainToken + "\"}," +
                "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}" + TEST_ACCOUNT;
        //String alloc = "\"" + iaasAddress + "\":{\"balance\":\"" + basicServiceChainToken + "\"}";//正式环境
        String tokenChain = ",\"noRewards\":false";

        GenesisParamDTO dto = GenesisParamDTO.builder()
                .chainId(chainId)
                .alloc(alloc)
                .cfd(cfd)
                .blockDelay(blockDelay)
                .tokenChain(tokenChain).build();
        return getGenesisJson(dto);
    }


    /**
     * 获取信任链的创世文件
     *
     * @param chainId    链id
     * @param tokenChain 共享币
     * @return
     */
    public String getTrustChainGenesisJson(String chainId, String tokenChain, int cfd, int blockDelay) {
        String alloc = "\"" + iaasAddress + "\":{\"balance\":\"" + transferTokenLimit + "\"}," +
                "\"0x1ceb7edecea8d481aa315b9a51b65c4def9b3dc6\":{\"balance\":\"10000000000000000000000000000000\"}" + TEST_ACCOUNT;
        //String alloc = "\"" + iaasAddress + "\":{\"balance\":\"" + transferTokenLimit + "\"}";//正式环境
        tokenChain = ",\"noRewards\":true,\"tokenChain\":" + tokenChain;
        GenesisParamDTO dto = GenesisParamDTO.builder()
                .chainId(chainId)
                .alloc(alloc)
                .cfd(cfd)
                .blockDelay(blockDelay)
                .tokenChain(tokenChain).build();
        return getGenesisJson(dto);
    }


    private String getGenesisJson(GenesisParamDTO dto) {
        String template = "{" +
                "  \"config\": {" +
                "    \"chainId\": %s," +
                "    \"homesteadBlock\": 0," +
                "    \"eip155Block\": 0," +
                "    \"eip158Block\": 0," +
                "    \"utopia\": {" +
                "      \"epoch\":30000," +
                "      \"cfd\":%s," +
                "      \"numMasters\":3," +
                "      \"blockDelay\":%s" +
                "       %s" +
                "    }" +
                "  }," +
                "  \"alloc\": {" +
                "       %s" +
                "}," +
                "  \"nonce\": \"0x0000000000000042\"," +
                "  \"difficulty\": \"0x1000\"," +
                "  \"mixhash\": \"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
                "  \"coinbase\": \"0x0000000000000000000000000000000000000000\"," +
                "  \"timestamp\": \"0x00\"," +
                "  \"parentHash\": \"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
                "  \"extraData\": \"\"," +
                "  \"gasLimit\": \"0x74523528\"" +
                "}";
        return String.format(template, dto.getChainId(), String.valueOf(dto.getCfd()), String.valueOf(dto.getBlockDelay()), dto.getTokenChain(), dto.getAlloc());
    }



}
