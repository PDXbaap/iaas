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

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class ThreadUtil {

    private static ExecutorService pool = Executors.newCachedThreadPool();

    public static void execute(Runnable task) {
        pool.execute(task);
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return pool.submit(task);
    }


    public static void sleep(int second) {
        try {
            Thread.sleep((long)second * 1000);
        } catch (InterruptedException e) {
            log.error("error >> thread sleep, error:{}", e);
            Thread.currentThread().interrupt();
        }
    }
}
