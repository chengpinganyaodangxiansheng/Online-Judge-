<template>
  <div class="add-exam-component-box">
    <div class="add-exam-component">
      <!-- 竞赛信息模块 -->
      <div class="exam-base-info-box">
        <!-- 标题 -->
        <div class="exam-base-title">
          <span class="base-title">{{ type === 'edit' ? '编辑竞赛' : '添加竞赛' }}</span>
          <span class="go-back" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </span>
        </div>
        <!-- 基本信息 -->
        <div class="exam-base-info">
          <div class="group-box">
            <div class="group-item">
              <div class="item-label required">竞赛名称</div>
              <div>
                <el-input v-model="formExam.title" class="custom-input" placeholder="请填写竞赛名称"></el-input>
              </div>
            </div>
          </div>
          <div class="group-box">
            <div class="group-item">
              <div class="item-label required">竞赛周期</div>
              <div>
                <el-date-picker 
                  v-model="formExam.examDate" 
                  :disabledDate="disabledDate" 
                  type="datetimerange"
                  start-placeholder="竞赛开始时间" 
                  end-placeholder="竞赛结束时间" 
                  value-format="YYYY-MM-DD HH:mm:ss"
                  class="custom-date-picker"
                />
              </div>
            </div>
          </div>
          <div class="group-box">
            <div class="group-item">
              <el-button class="exam-base-info-button" type="primary" @click="saveBaseInfo">
                <el-icon><Check /></el-icon>
                保存基本信息
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 添加竞赛题目 -->
      <div class="exam-select-question-box">
        <div class="section-header">
          <h3 class="section-title">竞赛题目</h3>
          <el-button class="exam-add-question" :icon="Plus" type="primary" @click="addQuestion()">
            添加题目
          </el-button>
        </div>
        <el-table :data="formExam.examQuestionList" class="question-select-list" stripe>
          <el-table-column prop="questionId" width="120px" label="题目ID" align="center" />
          <el-table-column prop="title" :show-overflow-tooltip="true" label="题目标题" min-width="200px" />
          <el-table-column prop="difficulty" width="100px" label="题目难度" align="center">
            <template #default="{ row }">
              <el-tag 
                v-if="row.difficulty === 1" 
                type="success" 
                effect="light"
                class="difficulty-tag easy"
              >
                简单
              </el-tag>
              <el-tag 
                v-if="row.difficulty === 2" 
                type="warning" 
                effect="light"
                class="difficulty-tag medium"
              >
                中等
              </el-tag>
              <el-tag 
                v-if="row.difficulty === 3" 
                type="danger" 
                effect="light"
                class="difficulty-tag hard"
              >
                困难
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100px" align="center">
            <template #default="{ row }">
              <el-button 
                type="danger" 
                size="small" 
                circle 
                @click="deleteExamQuestion(formExam.examId, row.questionId)"
                class="delete-btn"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 题目配置模块 题目列表勾选加序号 -->
      <div>
        <el-dialog v-model="dialogVisible" title="选择竞赛题目" width="80%" class="question-dialog">
          <div class="exam-list-box">
            <div class="search-form">
              <el-form inline="true">
                <el-form-item label="题目难度">
                  <selector v-model="params.difficulty" class="custom-selector"></selector>
                </el-form-item>
                <el-form-item label="题目名称">
                  <el-input v-model="params.title" placeholder="请您输入要搜索的题目标题" class="search-input" />
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
            
            <!-- 题目列表 -->
            <el-table :data="questionList" @select="handleRowSelect" stripe class="question-table">
              <el-table-column type="selection" width="55" align="center"></el-table-column>
              <el-table-column prop="questionId" label="题目ID" width="120" align="center" />
              <el-table-column prop="title" label="题目标题" min-width="200" show-overflow-tooltip />
              <el-table-column prop="difficulty" label="题目难度" width="100" align="center">
                <template #default="{ row }">
                  <el-tag 
                    v-if="row.difficulty === 1" 
                    type="success" 
                    effect="light"
                    class="difficulty-tag easy"
                  >
                    简单
                  </el-tag>
                  <el-tag 
                    v-if="row.difficulty === 2" 
                    type="warning" 
                    effect="light"
                    class="difficulty-tag medium"
                  >
                    中等
                  </el-tag>
                  <el-tag 
                    v-if="row.difficulty === 3" 
                    type="danger" 
                    effect="light"
                    class="difficulty-tag hard"
                  >
                    困难
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            
            <!-- 分页区域 -->
            <div class="exam-question-list-button">
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
              <el-button class="question-select-submit" type="primary" @click="submitSelectQuestion">
                <el-icon><Check /></el-icon>
                确认选择
              </el-button>
            </div>
          </div>
        </el-dialog>
      </div>

      <!-- 提交任务区域 -->
      <div class="submit-box">
        <el-button type="info" plain @click="goBack" class="cancel-btn">
          <el-icon><Close /></el-icon>
          取消
        </el-button>
        <el-button type="primary" @click="publishExam" class="publish-btn">
          <el-icon><Promotion /></el-icon>
          发布竞赛
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { examAddService, addExamQuestionService, getExamDetailService, editExamService, delExamQuestionService, publishExamService} from "@/apis/exam"
import { getQuestionListService } from "@/apis/question"
import Selector from "@/components/QuestionSelector.vue"
import router from '@/router'
import { reactive, ref } from "vue"
import { 
  Plus, 
  ArrowLeft, 
  Check, 
  Delete, 
  Search, 
  Refresh, 
  Close,
  Promotion 
} from '@element-plus/icons-vue'
import { useRoute } from 'vue-router';

const type = useRoute().query.type
const formExam = reactive({
  examId: '',
  title: '',
  examDate: ''
})

const params = reactive({
  pageNum: 1,
  pageSize: 10,
  difficulty: '',
  title: ''
})


// 返回
function goBack() {
  router.go(-1)
}

async function saveBaseInfo() {
  const fd = new FormData()
  for (let key in formExam) {
    if (key === 'examDate') {
      fd.append('startTime', formExam.examDate[0]);
      fd.append('endTime', formExam.examDate[1]);
    } else if (key !== 'startTime' && key !== 'endTime') {
      fd.append(key, formExam[key])
    }
  }
  if (formExam.examId) {
    //编辑
    await editExamService(fd)
  } else {
    const addRes = await examAddService(fd)
    formExam.examId = addRes.data
  }
  ElMessage.success('基本信息保存成功')
}

const questionList = ref([])
const total = ref(0)
async function getQuestionList() {
  const result = await getQuestionListService(params)
  console.log(result)
  questionList.value = result.rows
  total.value = result.total
}

const dialogVisible = ref(false)
function addQuestion() {
  if (formExam.examId === null || formExam.examId === '') {
    ElMessage.error('请先保存竞赛基本信息')
  } else {
    getQuestionList()
    dialogVisible.value = true
  }
}

function handleSizeChange() {
  params.pageNum = 1
  getQuestionList()
}

function handleCurrentChange() {
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

async function publishExam() {
  await publishExamService(formExam.examId)
  router.push("/oj/layout/exam")
}

const questionIdSet = ref([])

function handleRowSelect(selection) {
  questionIdSet.value = []
  selection.forEach(element => {
    questionIdSet.value.push(element.questionId)
  });
}

async function submitSelectQuestion() {
  if (questionIdSet.value && questionIdSet.value.length < 1) {
    ElMessage.error('请先选择要提交的题目')
    return false
  }
  const examQ = reactive({
    examId: formExam.examId,
    questionIdSet: questionIdSet.value
  })
  console.log(examQ)
  await addExamQuestionService(examQ);
  getExamDetailById(formExam.examId)
  dialogVisible.value = false
  ElMessage.success('竞赛题目添加成功')
}

async function getExamDetail() {
  const examId = useRoute().query.examId
  if (examId) {
    formExam.examId = examId
    getExamDetailById(examId)
  }
}
getExamDetail()

async function deleteExamQuestion(examId, questionId) {
  await delExamQuestionService(examId, questionId)
  getExamDetailById(examId)
  ElMessage.success('竞赛题目删除成功')
}

async function getExamDetailById(examId) {
  const examDetail = await getExamDetailService(examId)
  formExam.examQuestionList = []
  Object.assign(formExam, examDetail.data)
  formExam.examDate = [examDetail.data.startTime, examDetail.data.endTime]
}
</script>

<style lang="scss" scoped>
.add-exam-component-box {
  height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4efe9 100%);
  padding: 20px;
  overflow: auto;
}

.add-exam-component {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  padding: 0;
  overflow: hidden;
  min-height: calc(100vh - 40px);
  position: relative;
  padding-bottom: 100px;
}

.exam-base-info-box {
  background: #fff;
  margin-bottom: 20px;

  .exam-base-title {
    padding: 20px 24px;
    border-bottom: 1px solid #e9ecef;
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);

    .base-title {
      font-size: 18px;
      font-weight: 600;
      color: #333;
      position: relative;
      
      &::before {
        content: '';
        position: absolute;
        left: -12px;
        top: 50%;
        transform: translateY(-50%);
        width: 4px;
        height: 20px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-radius: 2px;
      }
    }

    .go-back {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #666;
      cursor: pointer;
      padding: 8px 16px;
      border-radius: 6px;
      transition: all 0.3s ease;
      font-size: 14px;
      
      &:hover {
        background: rgba(102, 126, 234, 0.1);
        color: #667eea;
        transform: translateX(-2px);
      }
      
      .el-icon {
        font-size: 16px;
      }
    }
  }

  .exam-base-info {
    padding: 24px;
    
    .group-box {
      margin-bottom: 24px;
      
      .group-item {
        display: flex;
        align-items: flex-start;
        gap: 24px;
        
        .item-label {
          font-size: 14px;
          font-weight: 600;
          color: #333;
          min-width: 100px;
          text-align: right;
          line-height: 36px;
          position: relative;
          
          &.required::before {
            content: '*';
            color: #f56c6c;
            margin-right: 4px;
          }
        }
        
        .custom-input {
          width: 420px;
          
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
        
        .custom-date-picker {
          width: 420px;
          
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
        
        .exam-base-info-button {
          border-radius: 8px;
          padding: 10px 24px;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border: none;
          font-weight: 500;
          margin-left: 100px;
          
          &:hover {
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
          }
          
          .el-icon {
            margin-right: 6px;
          }
        }
      }
    }
  }
}

.exam-select-question-box {
  padding: 0 24px 24px;
  
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    
    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #333;
      margin: 0;
      position: relative;
      
      &::before {
        content: '';
        position: absolute;
        left: -12px;
        top: 50%;
        transform: translateY(-50%);
        width: 4px;
        height: 16px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-radius: 2px;
      }
    }
    
    .exam-add-question {
      border-radius: 8px;
      padding: 8px 16px;
      
      .el-icon {
        margin-right: 6px;
      }
    }
  }
  
  .question-select-list {
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid #e9ecef;
    
    :deep(.el-table__header) {
      th {
        background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
        color: #333;
        font-weight: 600;
      }
    }
    
    .difficulty-tag {
      border: none;
      font-weight: 500;
      padding: 4px 8px;
      
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
    
    .delete-btn {
      color: #f56c6c;
      border-color: #f56c6c;
      
      &:hover {
        background: #f56c6c;
        color: white;
        transform: scale(1.1);
      }
    }
  }
}

// 对话框样式
:deep(.question-dialog) {
  .el-dialog {
    border-radius: 12px;
    overflow: hidden;
    
    .el-dialog__header {
      background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
      margin: 0;
      padding: 16px 24px;
      border-bottom: 1px solid #e9ecef;
      
      .el-dialog__title {
        font-weight: 600;
        color: #333;
      }
    }
  }
}

.exam-list-box {
  padding: 0;
  
  .search-form {
    background: #f8f9fa;
    padding: 16px 24px;
    border-bottom: 1px solid #e9ecef;
    
    .el-form {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
      
      :deep(.el-form-item) {
        margin-bottom: 0;
        
        .el-form-item__label {
          font-weight: 600;
          color: #333;
        }
      }
      
      .custom-selector {
        width: 140px;
        
        :deep(.el-input__wrapper) {
          border-radius: 6px;
        }
      }
      
      .search-input {
        width: 200px;
        
        :deep(.el-input__wrapper) {
          border-radius: 6px;
        }
      }
      
      .search-btn {
        border-radius: 6px;
        padding: 8px 16px;
        
        .el-icon {
          margin-right: 6px;
        }
      }
      
      .reset-btn {
        border-radius: 6px;
        padding: 8px 16px;
      }
    }
  }
  
  .question-table {
    margin: 0 24px;
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid #e9ecef;
    
    :deep(.el-table__header) {
      th {
        background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
        color: #333;
        font-weight: 600;
      }
    }
    
    .difficulty-tag {
      border: none;
      font-weight: 500;
      padding: 4px 8px;
      
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
  }
  
  .exam-question-list-button {
    padding: 16px 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    align-items: center;
    
    .custom-pagination {
      :deep(.el-pager li.is-active) {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-color: #667eea;
      }
    }
    
    .question-select-submit {
      border-radius: 8px;
      padding: 10px 24px;
      width: 200px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border: none;
      
      .el-icon {
        margin-right: 6px;
      }
    }
  }
}

.submit-box {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 16px 24px;
  border-top: 1px solid #e9ecef;
  display: flex;
  justify-content: center;
  gap: 16px;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  
  .cancel-btn {
    border-radius: 8px;
    padding: 10px 24px;
    
    .el-icon {
      margin-right: 6px;
    }
  }
  
  .publish-btn {
    border-radius: 8px;
    padding: 10px 24px;
    background: linear-gradient(135deg, #56ab2f 0%, #a8e6cf 100%);
    border: none;
    
    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(86, 171, 47, 0.4);
    }
    
    .el-icon {
      margin-right: 6px;
    }
  }
}

// 响应式设计
@media (max-width: 768px) {
  .add-exam-component-box {
    padding: 10px;
  }
  
  .exam-base-info {
    padding: 16px !important;
    
    .group-item {
      flex-direction: column;
      align-items: stretch !important;
      gap: 12px !important;
      
      .item-label {
        text-align: left !important;
      }
      
      .custom-input,
      .custom-date-picker {
        width: 100% !important;
      }
      
      .exam-base-info-button {
        margin-left: 0 !important;
        width: 100%;
      }
    }
  }
  
  .exam-select-question-box {
    padding: 0 16px 16px !important;
    
    .section-header {
      flex-direction: column;
      gap: 12px;
      align-items: stretch;
    }
  }
  
  .search-form .el-form {
    flex-direction: column;
    align-items: stretch;
    
    .el-form-item {
      width: 100%;
      
      .custom-selector,
      .search-input {
        width: 100% !important;
      }
    }
  }
  
  .submit-box {
    flex-direction: column;
    
    .el-button {
      width: 100%;
    }
  }
}
</style>

<style>
.w-e-text-container {
  min-height: 142px;
}
</style>