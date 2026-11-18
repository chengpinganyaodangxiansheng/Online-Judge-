<template>
  <!-- 搜索表单 -->
  <div class="search-form-container">
    <el-form inline="true" class="search-form">
      <el-form-item>
        <selector v-model="params.difficulty" placeholder="请选择题目难度" class="custom-selector"></selector>
      </el-form-item>
      <el-form-item>
        <el-input v-model="params.title" placeholder="请您输入要搜索的题目标题" class="custom-input" />
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
        <el-button type="success" :icon="Plus" @click="onAddQuestion" class="add-btn">
          添加题目
        </el-button>
      </el-form-item>
    </el-form>
  </div>

  <!-- 表格区域 -->
  <div class="table-container">
    <el-table 
      :data="questionList" 
      class="custom-table"
      stripe
      v-loading="loading"
    >
      <el-table-column prop="questionId" width="120px" label="题目ID" align="center" />
      <el-table-column prop="title" label="题目标题" min-width="200px" show-overflow-tooltip />
      <el-table-column prop="difficulty" label="题目难度" width="100px" align="center">
        <template #default="{ row }">
          <el-tag 
            v-if="row.difficulty === 1" 
            type="success" 
            effect="light"
            class="difficulty-tag easy"
          >
            <el-icon><SuccessFilled /></el-icon>
            简单
          </el-tag>
          <el-tag 
            v-if="row.difficulty === 2" 
            type="warning" 
            effect="light"
            class="difficulty-tag medium"
          >
            <el-icon><InfoFilled /></el-icon>
            中等
          </el-tag>
          <el-tag 
            v-if="row.difficulty === 3" 
            type="danger" 
            effect="light"
            class="difficulty-tag hard"
          >
            <el-icon><WarningFilled /></el-icon>
            困难
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createName" label="创建人" width="120px" align="center" />
      <el-table-column prop="createTime" label="创建时间" width="160px" align="center" />
      <el-table-column label="操作" width="140px" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button 
              type="primary" 
              size="small" 
              plain 
              @click="onEdit(row.questionId)"
              class="action-btn edit-btn"
            >
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button 
              type="danger" 
              size="small" 
              plain 
              @click="onDelete(row.questionId)"
              class="action-btn delete-btn"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
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

  <question-drawer ref="questionEditRef" @success="onSuccess"></question-drawer>
</template>

<script setup>
import { Plus, Search, Refresh, Edit, Delete, SuccessFilled, InfoFilled, WarningFilled } from "@element-plus/icons-vue"
import Selector from "@/components/QuestionSelector.vue"
import { getQuestionListService, delQuestionService } from "@/apis/question"
import { reactive, ref } from "vue";
import QuestionDrawer from "@/components/QuestionDrawer.vue"

const params = reactive({
  pageNum: 1,
  pageSize: 10,
  difficulty: '',
  title: ''
})

const questionList = ref([])
const total = ref(0)
const loading = ref(false)

async function getQuestionList() {
  loading.value = true
  try {
    const result = await getQuestionListService(params)
    console.log(result)
    questionList.value = result.rows
    total.value = result.total
  } finally {
    loading.value = false
  }
}
getQuestionList()

function handleSizeChange(newSize) {
  params.pageNum = 1
  getQuestionList()
}

function handleCurrentChange(newPage) {
  getQuestionList()
}

function onSearch() {
  params.pageNum = 1
  getQuestionList()
}

function onReset() {
  params.pageNum = 1
  params.pageSize = 10
  params.title = ''
  params.difficulty = ''
  getQuestionList()
}

const questionEditRef = ref()
// 添加
const onAddQuestion = () => {
  questionEditRef.value.open()
}

function onSuccess(service) {
  if (service === 'add') {
    params.pageNum = 1
  }
  getQuestionList()
}

async function onEdit(questionId) {
  questionEditRef.value.open(questionId)
}

async function onDelete(questionId) {
  await delQuestionService(questionId)
  params.pageNum = 1
  getQuestionList()
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
    }
    
    .custom-selector {
      width: 200px;
      
      :deep(.el-input__wrapper) {
        border-radius: 8px;
        transition: all 0.3s ease;
        
        &:hover {
          box-shadow: 0 0 0 1px #40ff93;
        }
        
        &.is-focus {
          box-shadow: 0 0 0 1px #40ff93;
        }
      }
    }
    
    .custom-input {
      width: 240px;
      
      :deep(.el-input__wrapper) {
        border-radius: 8px;
        transition: all 0.3s ease;
        
        &:hover {
          box-shadow: 0 0 0 1px #7cff40;
        }
        
        &.is-focus {
          box-shadow: 0 0 0 1px #40ff69;
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
    
    .difficulty-tag {
      display: flex;
      align-items: center;
      gap: 4px;
      font-weight: 500;
      border: none;
      padding: 6px 10px;
      
      .el-icon {
        font-size: 14px;
      }
      
      &.easy {
        color: #67c23a;
        background: rgba(103, 194, 58, 0.1);
      }
      
      &.medium {
        color: #e6a23c;
        background: rgba(230, 162, 60, 0.1);
      }
      
      &.hard {
        color: #f56c6c;
        background: rgba(245, 108, 108, 0.1);
      }
    }
    
    .action-buttons {
      display: flex;
      gap: 8px;
      justify-content: center;
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
        
        .custom-selector,
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
      min-width: 700px;
    }
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 4px !important;
  }
}
</style>