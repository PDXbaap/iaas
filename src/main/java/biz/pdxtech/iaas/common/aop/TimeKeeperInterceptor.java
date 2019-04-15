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

package biz.pdxtech.iaas.common.aop;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class TimeKeeperInterceptor {

    @Pointcut("execution(public * biz.pdxtech.iaas.rest.*.*(..))")
    private void pointCutMethodService() {
    }

    @Around("pointCutMethodService()")
    public Object doAroundService(ProceedingJoinPoint pjp) throws Throwable {

        long begin = System.currentTimeMillis();
        Object obj = pjp.proceed();
        long end = System.currentTimeMillis();

        //log.info("请求Rest:{}, 参数:{}, 执行耗时:{}毫秒", pjp.getSignature().toString().substring(31), Arrays.toString(pjp.getArgs()), (end - begin));
        //log.info("Request Rest API:{}, execute time:{}ms \n ", pjp.getSignature().toString().substring(31), (end - begin));
        long span = end - begin;
        if (span > 50){
            log.info(" ---   execute time: {}ms   ---   request api: {} \n ", (end - begin), pjp.getSignature().toString().substring(31));
        }
        return obj;

    }
}
