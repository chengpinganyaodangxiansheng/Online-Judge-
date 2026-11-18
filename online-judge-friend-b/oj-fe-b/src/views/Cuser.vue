<template>
  <!-- 搜索表单 -->
  <div class="search-form-container">
    <el-form inline="true" class="search-form">
      <el-form-item label="用户id">
        <el-input v-model="params.userId" placeholder="请您输入要搜索的用户id" class="custom-input" />
      </el-form-item>
      <el-form-item label="用户昵称">
        <el-input v-model="params.nickName" placeholder="请您输入要搜索的用户昵称" class="custom-input" />
      </el-form-item>
      <el-form-item>
        <el-button @click="onSearch" type="primary" class="search-btn">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button @click="onReset" plain class="reset-btn">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </div>

  <!-- 表格区域 -->
  <div class="table-container">
    <el-table 
      :data="userList" 
      class="custom-table"
      stripe
      v-loading="loading"
    >
      <el-table-column prop="userId" label="用户ID" width="120px" align="center" />
      <el-table-column prop="nickName" label="用户昵称" min-width="140px" />
      <el-table-column prop="sex" label="用户性别" width="100px" align="center">
        <template #default="{ row }">
          <div v-if="row.sex === 1" class="male-tag">
            <el-icon><Male /></el-icon>
            男
          </div>
          <div v-if="row.sex === 2" class="female-tag">
            <el-icon><Female /></el-icon>
            女
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="phone" width="140px" label="手机号" align="center" />
      <el-table-column prop="email" width="180px" label="邮箱" show-overflow-tooltip />
      <el-table-column prop="wechat" width="140px" label="微信号" align="center" />
      <el-table-column label="学校/专业" width="200px">
        <template #default="{ row }">
          <div class="school-info">
            <div class="school-item">
              <el-icon><School /></el-icon>
              <span>{{ row.schoolName || '未填写' }}</span>
            </div>
            <div class="school-item">
              <el-icon><Briefcase /></el-icon>
              <span>{{ row.majorName || '未填写' }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="introduce" label="个人介绍" min-width="200px" show-overflow-tooltip />
      <el-table-column prop="status" width="100px" label="用户状态" align="center">
        <template #default="{ row }">
          <el-tag type="success" effect="dark" v-if="row.status" class="status-tag">
            <el-icon><SuccessFilled /></el-icon>
            正常
          </el-tag>
          <el-tag type="danger" effect="dark" v-else class="status-tag">
            <el-icon><WarningFilled /></el-icon>
            拉黑
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120px" fixed="right" align="center">
        <template #default="{ row }">
          <el-button 
            v-if="row.status === 1" 
            type="danger" 
            size="small" 
            plain 
            @click="onUpdateUserStatus(row.userId, 0)"
            class="action-btn block-btn"
          >
            <el-icon><Remove /></el-icon>
            拉黑
          </el-button>
          <el-button 
            v-if="row.status === 0" 
            type="success" 
            size="small" 
            plain 
            @click="onUpdateUserStatus(row.userId, 1)"
            class="action-btn unblock-btn"
          >
            <el-icon><Check /></el-icon>
            解禁
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 分页区域 -->
  <div class="pagination-container">
    <el-pagination 
      background 
      layout="total, sizes, prev, pager, next, jumper" 
      :total="total"
      v-model:current-page="params.pageNum" 
      v-model:page-size="params.pageSize" 
      :page-sizes="[5, 10, 15, 20]"
      @size-change="handleSizeChange" 
      @current-change="handleCurrentChange"
      class="custom-pagination"
    />
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { getUserListService, updateStatusService } from '@/apis/cuser'
import { 
  Search, 
  Refresh, 
  Male, 
  Female, 
  School, 
  Briefcase, 
  SuccessFilled, 
  WarningFilled,
  Remove,
  Check
} from '@element-plus/icons-vue'

const params = reactive({
  pageNum: 1,
  pageSize: 10,
  userId: '',
  nickName: '',
})

const userList = ref([])
const total = ref(0)
const loading = ref(false)

async function getUserList() {
  loading.value = true
  try {
    const ref = await getUserListService(params)
    userList.value = ref.rows
    total.value = ref.total
  } finally {
    loading.value = false
  }
}
getUserList()

function onSearch() {
  params.pageNum = 1
  getUserList()
}

function onReset() {
  params.pageNum = 1
  params.pageSize = 10
  params.userId = ''
  params.nickName = ''
  getUserList()
}

function handleSizeChange(newSize) {
  params.pageNum = 1
  getUserList()
}

function handleCurrentChange(newPage) {
  getUserList()
}

const updateStatusParams = reactive({
  userId: '',
  status: '',
})

async function onUpdateUserStatus(userId, status) {
  updateStatusParams.userId = userId
  updateStatusParams.status = status
  await updateStatusService(updateStatusParams)
  getUserList()
}
</script>

<style lang="scss" scoped>
.search-form-container {
  background: #fff;
  padding: 20px;
  margin-bottom: 16px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  
  .search-form {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;
    
    :deep(.el-form-item) {
      margin-bottom: 0;
      
      .el-form-item__label {
        font-weight: 600;
        color: #333;
        font-size: 14px;
      }
    }
    
    .custom-input {
      width: 200px;
      
      :deep(.el-input__wrapper) {
        border-radius: 8px;
        transition: all 0.3s ease;
        
        &:hover {
          box-shadow: 0 0 0 1px #409EFF;
        }
        
        &.is-focus {
          box-shadow: 0 0 0 1px #409EFF;
        }
      }
    }
    
    .search-btn {
      border-radius: 8px;
      padding: 10px 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border: none;
      font-weight: 500;
      
      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
      }
    }
    
    .reset-btn {
      border-radius: 8px;
      padding: 10px 20px;
      border-color: #dcdfe6;
      
      &:hover {
        color: #667eea;
        border-color: #667eea;
        background: rgba(102, 126, 234, 0.05);
      }
    }
  }
}

.table-container {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  margin-bottom: 16px;
  
  .custom-table {
    :deep(.el-table__header) {
      th {
        background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
        color: #333;
        font-weight: 600;
        border-bottom: 2px solid #e9ecef;
      }
    }
    
    :deep(.el-table__body) {
      tr:hover > td {
        background: rgba(102, 126, 234, 0.04) !important;
      }
    }
    
    .male-tag {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      color: #3EC8FF;
      font-weight: 500;
      
      .el-icon {
        font-size: 16px;
      }
    }
    
    .female-tag {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      color: #FD4C40;
      font-weight: 500;
      
      .el-icon {
        font-size: 16px;
      }
    }
    
    .school-info {
      .school-item {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-bottom: 4px;
        font-size: 12px;
        color: #666;
        
        &:last-child {
          margin-bottom: 0;
        }
        
        .el-icon {
          color: #999;
          font-size: 14px;
        }
        
        span {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
    
    .status-tag {
      display: flex;
      align-items: center;
      gap: 4px;
      border: none;
      font-weight: 500;
      
      .el-icon {
        font-size: 14px;
      }
    }
    
    .action-btn {
      border-radius: 6px;
      font-weight: 500;
      padding: 6px 12px;
      transition: all 0.3s ease;
      
      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
      }
      
      .el-icon {
        margin-right: 4px;
        font-size: 14px;
      }
    }
    
    .block-btn {
      border-color: #f56c6c;
      color: #f56c6c;
      
      &:hover {
        background: #f56c6c;
        color: white;
      }
    }
    
    .unblock-btn {
      border-color: #67c23a;
      color: #67c23a;
      
      &:hover {
        background: #67c23a;
        color: white;
      }
    }
  }
}

.pagination-container {
  display: flex;
  justify-content: center;
  background: #fff;
  padding: 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  
  .custom-pagination {
    :deep(.btn-prev),
    :deep(.btn-next),
    :deep(.number) {
      border-radius: 8px;
      margin: 0 4px;
    }
    
    :deep(.el-pager li.is-active) {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-color: #667eea;
    }
  }
}

// 响应式设计
@media (max-width: 768px) {
  .search-form-container {
    padding: 16px;
    
    .search-form {
      flex-direction: column;
      align-items: stretch;
      
      :deep(.el-form-item) {
        width: 100%;
        
        .custom-input {
          width: 100%;
        }
      }
    }
  }
  
  .table-container {
    overflow-x: auto;
    
    .custom-table {
      min-width: 800px;
    }
  }
}
</style>