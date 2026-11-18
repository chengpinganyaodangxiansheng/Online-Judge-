<template>
  <!-- 搜索表单 -->
  <div class="search-form-container">
    <el-form inline="true" class="search-form">
      <el-form-item label="创建日期">
        <el-date-picker 
          v-model="datetimeRange" 
          type="datetimerange" 
          range-separator="至"
          start-placeholder="开始日期" 
          end-placeholder="结束日期"
          class="custom-date-picker"
        ></el-date-picker>
      </el-form-item>
      <el-form-item label="竞赛名称">
        <el-input v-model="params.title" placeholder="请您输入要搜索的竞赛名称" class="custom-input" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch" class="search-btn">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button plain @click="onReset" class="reset-btn">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
        <el-button type="success" :icon="Plus" @click="onAddExam" class="add-btn">
          添加竞赛
        </el-button>
      </el-form-item>
    </el-form>
  </div>

  <!-- 表格区域 -->
  <div class="table-container">
    <el-table 
      :data="examList" 
      class="custom-table"
      stripe
      v-loading="loading"
    >
      <el-table-column prop="title" label="竞赛标题" min-width="200px" show-overflow-tooltip />
      <el-table-column prop="startTime" width="180px" label="开始时间" align="center" />
      <el-table-column prop="endTime" width="180px" label="结束时间" align="center" />
      <el-table-column label="竞赛状态" width="100px" align="center">
        <template #default="{ row }">
          <el-tag 
            v-if="!isNotStartExam(row)" 
            type="warning" 
            effect="light"
            class="status-tag started"
          >
            <el-icon><Clock /></el-icon>
            已开赛
          </el-tag>
          <el-tag 
            v-else 
            type="info" 
            effect="light"
            class="status-tag not-started"
          >
            <el-icon><Timer /></el-icon>
            未开赛
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" width="100px" label="发布状态" align="center">
        <template #default="{ row }">
          <el-tag 
            v-if="row.status == 0" 
            type="danger" 
            effect="light"
            class="publish-tag unpublished"
          >
            <el-icon><CloseBold /></el-icon>
            未发布
          </el-tag>
          <el-tag 
            v-if="row.status == 1" 
            type="success" 
            effect="light"
            class="publish-tag published"
          >
            <el-icon><Check /></el-icon>
            已发布
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createName" width="120px" label="创建人" align="center" />
      <el-table-column prop="createTime" width="160px" label="创建时间" align="center" />
      <el-table-column label="操作" width="200px" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button 
              v-if="isNotStartExam(row) && row.status == 0" 
              type="primary" 
              size="small" 
              plain 
              @click="onEdit(row.examId)"
              class="action-btn edit-btn"
            >
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button 
              v-if="isNotStartExam(row) && row.status == 0" 
              type="danger" 
              size="small" 
              plain 
              @click="onDelete(row.examId)"
              class="action-btn delete-btn"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
            <el-button 
              v-if="row.status == 1 && isNotStartExam(row)" 
              type="warning" 
              size="small" 
              plain 
              @click="cancelPublishExam(row.examId)"
              class="action-btn cancel-btn"
            >
              <el-icon><Close /></el-icon>
              撤销发布
            </el-button>
            <el-button 
              v-if="row.status == 0 && isNotStartExam(row)" 
              type="success" 
              size="small" 
              plain 
              @click="publishExam(row.examId)"
              class="action-btn publish-btn"
            >
              <el-icon><Check /></el-icon>
              发布
            </el-button>
            <el-tag 
              v-if="!isNotStartExam(row)" 
              type="info" 
              size="small"
              class="no-action-tag"
            >
              已开赛
            </el-tag>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 分页区域 -->
  <div class="pagination-container">
    <el-pagination 
      background 
      size="small" 
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
import { 
  Plus, 
  Search, 
  Refresh, 
  Edit, 
  Delete, 
  Check, 
  Close,
  CloseBold,
  Clock,
  Timer
} from '@element-plus/icons-vue'
import { getExamListService, delExamService, publishExamService, cancelPublishExamService } from '@/apis/exam'
import { reactive, ref } from 'vue'
import router from '@/router'

function isNotStartExam(exam) {
  const now = new Date(); //当前时间
  return new Date(exam.startTime) > now
}

const params = reactive({
  pageNum: 1,
  pageSize: 10,
  startTime:'',
  endTime:'',
  title: ''
})

const examList = ref([])
const total = ref(0)
const datetimeRange = ref([])
const loading = ref(false)

async function getExamList() {
  loading.value = true
  try {
    if (datetimeRange.value[0] instanceof Date) {
      params.startTime = formatDateTime(datetimeRange.value[0])
    }
    if (datetimeRange.value[1] instanceof Date) {
      params.endTime = formatDateTime(datetimeRange.value[1])
    }
    const result = await getExamListService(params)
    examList.value = result.rows
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function formatDateTime(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

getExamList()

function handleSizeChange(newSize) {
  params.pageNum = 1
  getExamList()
}

function handleCurrentChange(newPage) {
  getExamList()
}

function onSearch() {
  params.pageNum = 1
  getExamList()
}

function onReset() {
  params.pageNum = 1
  params.pageSize = 10
  params.title = ''
  params.startTime = ''
  params.endTime = ''
  datetimeRange.value.length = 0
  getExamList()
}

function onAddExam() {
  //跳转到新的页面上   
  router.push("/oj/layout/updateExam?type=add")
}

async function onEdit(examId) {
  // await getExamDetailService(examId)
  router.push(`/oj/layout/updateExam?type=edit&examId=${examId}`)
  ///oj/layout/updateExam?type=edit&examId=${examId}
  ///oj/layout/updateExam?type=edit&examId=123
}

async function onDelete(examId) {
  await delExamService(examId)
  params.pageNum = 1
  getExamList()
}

async function publishExam(examId) {
  await publishExamService(examId)
  getExamList()
}

async function cancelPublishExam(examId) {
  await cancelPublishExamService(examId)
  getExamList()
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
    
    .custom-date-picker {
      width: 320px;
      
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
    
    .custom-input {
      width: 240px;
      
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
      
      .el-icon {
        margin-right: 6px;
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
      
      .el-icon {
        margin-right: 6px;
      }
    }
    
    .add-btn {
      border-radius: 8px;
      padding: 10px 20px;
      background: linear-gradient(135deg, #56ab2f 0%, #a8e6cf 100%);
      border: none;
      font-weight: 500;
      
      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 4px 12px rgba(86, 171, 47, 0.4);
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
    
    .status-tag {
      display: flex;
      align-items: center;
      gap: 4px;
      font-weight: 500;
      border: none;
      padding: 6px 10px;
      
      .el-icon {
        font-size: 14px;
      }
      
      &.started {
        color: #e6a23c;
        background: rgba(230, 162, 60, 0.1);
      }
      
      &.not-started {
        color: #909399;
        background: rgba(144, 147, 153, 0.1);
      }
    }
    
    .publish-tag {
      display: flex;
      align-items: center;
      gap: 4px;
      font-weight: 500;
      border: none;
      padding: 6px 10px;
      
      .el-icon {
        font-size: 14px;
      }
      
      &.published {
        color: #67c23a;
        background: rgba(103, 194, 58, 0.1);
      }
      
      &.unpublished {
        color: #f56c6c;
        background: rgba(245, 108, 108, 0.1);
      }
    }
    
    .action-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      justify-content: center;
    }
    
    .action-btn {
      border-radius: 6px;
      font-weight: 500;
      padding: 6px 10px;
      font-size: 12px;
      transition: all 0.3s ease;
      
      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
      }
      
      .el-icon {
        margin-right: 4px;
        font-size: 12px;
      }
    }
    
    .edit-btn {
      border-color: #409EFF;
      color: #409EFF;
      
      &:hover {
        background: #409EFF;
        color: white;
      }
    }
    
    .delete-btn {
      border-color: #f56c6c;
      color: #f56c6c;
      
      &:hover {
        background: #f56c6c;
        color: white;
      }
    }
    
    .publish-btn {
      border-color: #67c23a;
      color: #67c23a;
      
      &:hover {
        background: #67c23a;
        color: white;
      }
    }
    
    .cancel-btn {
      border-color: #e6a23c;
      color: #e6a23c;
      
      &:hover {
        background: #e6a23c;
        color: white;
      }
    }
    
    .no-action-tag {
      border: none;
      background: rgba(144, 147, 153, 0.1);
      color: #909399;
      font-size: 12px;
      padding: 6px 10px;
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
        
        .custom-date-picker,
        .custom-input {
          width: 100%;
        }
      }
      
      .el-form-item:last-child {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        
        .el-button {
          flex: 1;
          min-width: 120px;
        }
      }
    }
  }
  
  .table-container {
    overflow-x: auto;
    
    .custom-table {
      min-width: 900px;
    }
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 4px !important;
  }
}
</style>