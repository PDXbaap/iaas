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

package biz.pdxtech.iaas.cluster;

import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;

@Component
public class ClusterRunner implements CommandLineRunner, Runnable, Watcher {

    private static final Logger log = Logger.getLogger(ClusterRunner.class.getName());

    private final Partitioner partitioner;

    @Autowired
    public ClusterRunner(Partitioner partitioner) {
        this.partitioner = partitioner;
    }

    @Value("${pdx.iaas.zk.connectString}")
    String connectString;
    @Value("${pdx.iaas.zk.sessionTimeout}")
    int sessionTimeout;
    @Value("${pdx.iaas.zk.znode.group}")
    String znode;
    @Value("${pdx.iaas.zk.znode.trustChain.tailChains}")
    String trustChainTailChain;
    @Value("${pdx.iaas.zk.znode.trustChain.firstNode}")
    String trustChainFirstNode;
    @Value("${pdx.iaas.zk.znode.bizChain.firstNode}")
    String bizChainFirstNode;
    @Value("${pdx.iaas.zk.znode.scan.order}")
    String scanOrder;
    @Value("${pdx.iaas.zk.znode.chain.firstNode}")
    private String chainFirstNode;


    ZooKeeper zk;
    volatile boolean connected;
    public String uuid = UUID.randomUUID().toString();


    @Override
    public void run(String... args) throws Exception {

        log.info("entering self-org runner...");

        log.info(this.connectString + " " + this.sessionTimeout + " " + this.znode);

        log.info("done with self-org runner.");

        new Thread(this).start();
    }

    @Override
    public void run() {

        this.partitioner.setSelf(uuid);

        this.connect();

        // periodic call to keep alive the zookeeper session
        Timer timer = new Timer(true);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (connected) {
                        if (null == zk.exists(znode + "/" + uuid, true)) {
                            log.info("zookeeper heartbeat: someone deleted me");
                            register();
                        } else {
                            log.info("zookeeper heartbeat: i am still alive");
                        }
                    }
                } catch (KeeperException | InterruptedException ex) {
                    log.severe(ex.toString());
                }
            }
        }, 150000, 150000);

    }

    // Auto-adjust the blockchain ecosystem
    public void adjust(List<String> members) {
        this.partitioner.adjust(members, this);
    }

    @Override
    public void process(WatchedEvent we) {

        log.info("processing watcher event: " + we.toString());

        switch (we.getType()) {

            case None:
                // state of the connection has changed
                switch (we.getState()) {
                    case SyncConnected:
                        log.info("zookeeper connected, starting up");
                        this.connected = true;
                        this.startup();
                        break;
                    case Expired:
                    case Disconnected:
                    case ConnectedReadOnly:
                        log.info("zookeeper " + we.getState().name() + " , re-connecting");
                        this.connected = false;
                        this.connect();
                        break;
                }

                break;

            case NodeChildrenChanged:

                if (we.getPath() != null && we.getPath().matches(this.znode)) {
                    try {
                        this.adjust(this.getChildren(znode, this));
                    } catch (KeeperException | InterruptedException | UnsupportedEncodingException ex) {
                        log.severe(ex.toString());
                    }
                }

                break;

            default:

                log.info("received event of no interest");

                break;
        }
    }

    void startup() {

        try {

            createZookeeperNode(znode);
            createZookeeperNode(trustChainFirstNode);
            createZookeeperNode(trustChainTailChain);
            createZookeeperNode(bizChainFirstNode);
            createZookeeperNode(scanOrder);
            createZookeeperNode(chainFirstNode);

            register();

            try {
                List<String> members = this.getChildren(znode, this);

                this.adjust(members);

            } catch (UnsupportedEncodingException ex) {
                log.severe(ex.toString());
            }

        } catch (KeeperException | InterruptedException ex) {
            log.severe(ex.toString());
        }

    }

    private void createZookeeperNode(String node) throws KeeperException, InterruptedException {
        String[] subp = node.split("/");
        String path = "/";

        for (int i = 0; i < subp.length; i++) {
            if (subp[i].length() == 0) {
                continue;
            }
            path += subp[i];

            if (null == this.exists(path)) {
                this.create(path, uuid.getBytes(), CreateMode.PERSISTENT);
            }
            path += "/";
        }
    }

    private void register() throws KeeperException, InterruptedException {
        String self = uuid;

        this.create(znode + "/" + uuid, self.getBytes(), CreateMode.EPHEMERAL);

    }

    private void connect() {

        try {
            zk = new ZooKeeper(this.connectString, this.sessionTimeout, this);

        } catch (IOException ex) {
            log.severe(ex.toString());
        }

    }

    public void create(String path, byte[] data, CreateMode mode) throws
            KeeperException, InterruptedException {
        zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, mode);

    }

    public Stat exists(String path) throws KeeperException, InterruptedException {

        Stat stat = zk.exists(path, true);

        if (stat != null) {
            log.info("Node exists and the node version is " + stat.getVersion());
        } else {
            log.info("Node does not exists");
        }

        return stat;

    }

    public byte[] getData(String path, boolean watch) throws KeeperException, InterruptedException, UnsupportedEncodingException {

        Stat stat = this.exists(path);

        if (stat == null) {
            log.info("znode: " + path + " does not exist.");

            return null;

        }

        byte[] data = zk.getData(path, this, null);

        return data;

    }


    public void update(String path, byte[] data) throws
            KeeperException, InterruptedException {
        zk.setData(path, data, zk.exists(path, true).getVersion());

    }

    public List<String> getChildren(String path, Watcher w) throws KeeperException, InterruptedException, UnsupportedEncodingException {

        Stat stat = this.exists(path);

        if (stat == null) {
            log.info("znode: " + path + " does not exist.");
            return null;
        }

        List<String> children = zk.getChildren(path, this, null);

        return children;

    }


    public List<String> getChildren(String path) throws KeeperException, InterruptedException {
        Stat stat = this.exists(path);
        if (stat == null) {
            log.info("znode: " + path + " does not exist.");
            return null;
        }
        List<String> children = zk.getChildren(path, this, null);
        return children;
    }


    public void delete(String path) throws KeeperException, InterruptedException {
        zk.delete(path, zk.exists(path, true).getVersion());
    }
}
