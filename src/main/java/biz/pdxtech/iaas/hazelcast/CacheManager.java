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

import biz.pdxtech.iaas.entity.Chain;
import biz.pdxtech.iaas.entity.Chain_Node;
import biz.pdxtech.iaas.entity.Node;
import biz.pdxtech.iaas.repository.ChainNodeRepository;
import biz.pdxtech.iaas.repository.ChainRepository;
import biz.pdxtech.iaas.repository.NodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager implements CommandLineRunner {

    @Autowired
    ChainNodeRepository chainNodeRepository;
    @Autowired
    NodeRepository nodeRepository;
    @Autowired
    ChainRepository chainRepository;

    private static final long ATTACK_TIME = 180;
    private static final long IP_ID_KEY_TIME = 30;
    private static final long IP_KEY_TIME = 30;

    private static final String ATTACK = "_attack";


    public void update(String ip) {
        CacheData.getInstance().getCacheData().put(ip, System.currentTimeMillis(), IP_KEY_TIME, TimeUnit.SECONDS);
    }

    private void update(String ip, Long chainId) {
        CacheData.getInstance().getCacheData().put(ip + chainId, System.currentTimeMillis(), IP_ID_KEY_TIME, TimeUnit.SECONDS);
    }

    private void updateAttack(String ip) {
        CacheData.getInstance().getCacheData().put(ip + ATTACK, System.currentTimeMillis(), ATTACK_TIME, TimeUnit.SECONDS);
    }

    private void updateAttack(String ip, Long chainId) {
        CacheData.getInstance().getCacheData().put(ip + chainId + ATTACK, System.currentTimeMillis(), ATTACK_TIME, TimeUnit.SECONDS);
    }

    private boolean isAttack(String ip) {
        return CacheData.getInstance().getCacheData().containsKey(ip + ATTACK);
    }

    private boolean isAttack(String ip, Long chainId) {
        return CacheData.getInstance().getCacheData().containsKey(ip + chainId + ATTACK);
    }


    /**
     * 检查节点更新缓存
     *
     * @param ip      ip地址
     * @param chainId 链id
     * @param status  状态
     * @return
     */
    public boolean checkCacheForUpdate(String ip, Long chainId, String status) {

        if (isAttack(ip) || isAttack(ip, chainId)) {
            return false;
        }

        Object createTime = CacheData.getInstance().getCacheData().get(ip);
        if (Objects.isNull(createTime)) {
            Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);
            if (Objects.isNull(node)) {
                log.warn("Server not register, assert illegal request >> ip:{} ", ip);
                updateAttack(ip);
                updateAttack(ip, chainId);
                return false;
            } else {
                update(ip);
            }
        }

        Chain chain = chainRepository.findChainByChainId(chainId);
        if (Objects.isNull(chain)) {
            log.warn("Not found chain, assert illegal request >> ip:{}, chainId:{} ", ip, chainId);
            updateAttack(ip, chainId);
            return false;
        } else {
            if (status.equals("1")) {
                update(ip, chainId);
            }
        }

        return true;
    }


    /**
     * 检查static node缓存
     *
     * @param ip      ip地址
     * @param chainId 链id
     * @param method  方法名
     * @return
     */
    public boolean checkCacheForStaticNode(String ip, Long chainId, String method) {

        if (isAttack(ip) || isAttack(ip, chainId)) {
            log.warn("illegal request {}  >>  ip:{}, chainId:{} \n", method, ip, chainId);
            return false;
        }

        boolean existed = CacheData.getInstance().getCacheData().containsKey(ip);
        if (!existed) {

            Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);
            Chain chain = chainRepository.findChainByChainIdAndStatIsNot(chainId, Chain.Stat.REMOVED);
            if (Objects.isNull(node) || Objects.isNull(chain)) {
                log.warn("assert illegal request {}  >>  ip:{}, chainId:{} \n", method, ip, chainId);
                updateAttack(ip, chainId);
                return false;
            }
        }
        return true;
    }


    /**
     * 检查缓存
     *
     * @param ip      ip地址
     * @param chainId 链id
     * @param method  方法名
     * @return
     */
    public boolean checkCache(String ip, Long chainId, String method) {

        if (isAttack(ip) || isAttack(ip, chainId)) {
            log.warn("illegal request {}  >>  ip:{}, chainId:{} \n", method, ip, chainId);
            return false;
        }

        String key = ip + chainId.toString();
        boolean existed = CacheData.getInstance().getCacheData().containsKey(key);
        if (!existed) {

            Node node = nodeRepository.findNodeByIpAndStatIsNot(ip, Node.Stat.REMOVED);
            if (Objects.isNull(node)) {
                log.warn("Not found node, assert illegal request {}  >>  ip:{}, chainId:{} \n", method, ip, chainId);
                updateAttack(ip, chainId);
                return false;
            } else {
                update(ip);
            }

            Chain_Node chainNode = chainNodeRepository.findChain_NodeByChain_ChainIdAndNode_IdAndStatIsNot(chainId, node.getId(), Chain_Node.Stat.REMOVED);
            if (Objects.isNull(chainNode)) {
                log.warn("Not found chain node, assert illegal request {}  >>  ip:{}, chainId:{} \n", method, ip, chainId);
                updateAttack(ip, chainId);
                return false;
            } else {
                update(ip, chainId);
                return true;
            }
        }
        update(ip, chainId);
        return true;
    }


    /**
     * 检查block频率
     *
     * @param ip      ip地址
     * @param chainId 链id
     * @return
     */
    public boolean checkConfirmFrequency(String ip, Long chainId) {

        String confirmedKey = ip + chainId.toString() + "confirm";
        String timeoutKey = chainId.toString() + "timeout";
        Object timeoutValue = CacheData.getInstance().getCacheData().get(timeoutKey);
        if (Objects.isNull(timeoutValue)) {
            Chain chain = chainRepository.findChainByChainId(chainId);
            int timeout = chain.getCfd() * chain.getBlockDelay();
            CacheData.getInstance().getCacheData().put(timeoutKey, timeout, 2, TimeUnit.HOURS);
            timeoutValue = String.valueOf(timeout);
        }

        boolean confirmed = CacheData.getInstance().getCacheData().containsKey(confirmedKey);
        if (confirmed) {
            log.warn("comfirm block frequently, suspected attack  >>  ip:{}, chainId:{} \n", ip, chainId);
            return false;
        } else {
            int timeout = Integer.parseInt(String.valueOf(timeoutValue));
            CacheData.getInstance().getCacheData().put(confirmedKey, System.currentTimeMillis(), timeout, TimeUnit.MILLISECONDS);
            return true;
        }
    }


    @Override
    public void run(String... args) throws Exception {
        log.info("CacheManager start ...");
        CacheData.getInstance().getCacheData().clear();
    }
}
