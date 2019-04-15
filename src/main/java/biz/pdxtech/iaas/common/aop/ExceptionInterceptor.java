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

import biz.pdxtech.iaas.proto.RespEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class ExceptionInterceptor {

    private Logger log = LoggerFactory.getLogger("GlobalExceptionHandler");

    /**
     * 应用到所有@RequestMapping注解方法，在其执行之前初始化数据绑定器
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
    }

    /**
     * 把值绑定到Model中，使全局@RequestMapping可以获取到该值
     */
    @ModelAttribute
    public void addAttributes(Model model) {
        model.addAttribute("web", "Canaanwed");
    }

    /**
     * 全局异常捕捉处理
     */
    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public RespEntity errorHandler(Exception e, HttpServletRequest request) {
        String messageValue = "网络连接错误，请稍后重试";
        if (e instanceof MethodArgumentTypeMismatchException) {
            messageValue = "参数类型不匹配";
        } else if (e instanceof HttpRequestMethodNotSupportedException) {
            messageValue = "请求类型不匹配";
        } else if (e instanceof MissingServletRequestParameterException) {
            messageValue = "缺少参数";
        }
        e.printStackTrace();
        Map<String, String> parameters = getParameterStringMap(request);
        log.error("请求接口 [{}] 异常 ", request.getRequestURL());
        parameters.forEach((key, val) -> log.error("请求参数 [{}] 值是[{}]", key, val));
        log.error("异常信息 {}", e);
        return new RespEntity(500, messageValue);
    }

    private static Map<String, String> getParameterStringMap(HttpServletRequest request) {
        Map<String, String[]> properties = request.getParameterMap();
        Map<String, String> returnMap = new HashMap<>();
        String name;
        String value = "";
        for (Map.Entry<String, String[]> entry : properties.entrySet()) {
            name = entry.getKey();
            String[] values = entry.getValue();
            if (null == values) {
                value = "";
            } else if (values.length > 1) {
                for (int i = 0; i < values.length; i++) {
                    value = values[i] + ",";
                }
                value = value.substring(0, value.length() - 1);
            } else {
                value = values[0];
            }
            returnMap.put(name, value);
        }
        return returnMap;
    }


}