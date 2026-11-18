package org.example.common.core.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.github.pagehelper.PageInfo;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;

import java.util.List;

public class BaseController {


    public Result toResult(int rows) {
        return rows > 0 ? Result.success() : Result.fail();
    }


    public Result toResult(boolean result) {
        return result ? Result.success() : Result.fail();
    }

    public TableDataInfo getDataTable(List<?> list) {
        //collection == null || collection.isEmpty();
        if (CollectionUtil.isEmpty(list)) {
            return TableDataInfo.empty();
        }
        long total = new PageInfo<>(list).getTotal();//获取符合条件的数据总数
        //此处不能使用list.size(),list.size()计算的结果只有当前页面的数据量，而不是全部的数据量,这里的total是指每页数据的总和
        //return TableDataInfo.success(list,list.size());
        return TableDataInfo.success(list,total);
    }

}
