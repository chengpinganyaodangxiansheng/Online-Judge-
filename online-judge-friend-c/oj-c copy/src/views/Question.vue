<template>
  <div class="index-question">
    <div class="index-question-table-box">
      <page-container class="left">
        <el-form inline="true" ref="formModel" :model="form" class="search-form">
          <el-form-item class="search-input-item">
            <el-input v-model="params.keyword" placeholder="请您输入题目标题或内容" class="keyword-input" />
          </el-form-item>
          <el-form-item class="selector-item">
            <selector v-model="params.difficulty" class="difficulty-selector"></selector>
          </el-form-item>
          <el-form-item class="button-group">
            <el-button type="primary" @click="onSearch" class="search-btn">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button plain @click="onReset" class="reset-btn">
              <el-icon><Refresh /></el-icon>
              重置
            </el-button>
          </el-form-item>
        </el-form>
        <!-- 表格 -->
        <el-table :data="questionList" height="580px" class="question-table" stripe>
          <el-table-column align="center" width="80px" label="序号">
            <template #default="{ $index }">
              <span class="index-number">{{ (params.pageNum - 1) * params.pageSize + $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column align="left" prop="title" :show-overflow-tooltip="true" label="题目标题" min-width="300px" />
          <el-table-column align="center" width="120px" prop="difficulty" label="题目难度">
            <template #default="{ row }">
              <el-tag v-if="row.difficulty === 1" type="success" effect="light" class="difficulty-tag easy">
                简单
              </el-tag>
              <el-tag v-if="row.difficulty === 2" type="warning" effect="light" class="difficulty-tag medium">
                中等
              </el-tag>
              <el-tag v-if="row.difficulty === 3" type="danger" effect="light" class="difficulty-tag hard">
                困难
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="180px">
            <template #default="{ row }">
              <el-button type="primary" size="small" plain v-if="isLogin" @click="goQuestTest(row.questionId)" class="start-btn">
                <el-icon><Right /></el-icon>
                开始答题
              </el-button>
              <span class="login-tip" v-else>请登录后参与答题</span>
            </template>
          </el-table-column>
        </el-table>
        <!-- 分页区域 -->
        <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="total"
          v-model:current-page="params.pageNum" v-model:page-size="params.pageSize" :page-sizes="[5, 10, 15, 20]"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" class="custom-pagination" />
      </page-container>
      <div class="right">
        <div class="top-box">
          <el-calendar v-model="today"> </el-calendar>
        </div>
        <div class="bot-box">
          <div class="title">
            <img width="96px" height="24px" src="@/assets/rebang.png" alt="">
          </div>
          <div class="hot-list">
            <div class="list-item" v-for="(item, index) in hotQuestionList" :key="'hot_' + index">
              <img class="index-box" v-if="index == 0" src="@/assets/images/icon_1.png" alt="">
              <img class="index-box" v-if="index == 1" src="@/assets/images/icon_2.png" alt="">
              <img class="index-box" v-if="index == 2" src="@/assets/images/icon_3.png" alt="">
              <span class="index-box number" v-if="index > 2">{{ index + 1 }}</span>
              <span class="txt" :title="item.title">{{ item.title }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue"
import Selector from "@/components/QuestionSelector.vue"
import { getQuestionListService,getHotQuestionListService } from '@/apis/question'
import { getToken } from "@/utils/cookie"
import router from "@/router"
import { getUserInfoService } from "@/apis/user"
import { Search, Refresh, Right } from '@element-plus/icons-vue'

const params = reactive({
  pageNum: 1,
  pageSize: 10,
  difficulty: '',
  keyword: ''
})
let today = ref(new Date()) //日历默认今天
const questionList = ref([]) //题目列表
const total = ref(0)
const isLogin = ref(false)

async function checkLogin() {
  if (getToken()) {
    await getUserInfoService()
    isLogin.value = true
  }
}
checkLogin()

async function getQuestionList() {
  const result = await getQuestionListService(params)
  questionList.value = result.rows
  total.value = result.total
}
getQuestionList()

const hotQuestionList = ref([])
async function getHotQuestionList(params) {
  const hotRef = await getHotQuestionListService()
  hotQuestionList.value = hotRef.data
}

getHotQuestionList()

// 搜索/重置
function onSearch() {
  params.pageNum = 1
  getQuestionList()
}

function onReset() {
  params.pageNum = 1
  params.pageSize = 9
  params.difficulty = ''
  params.keyword = ''
  getQuestionList()
}

// 分页
function handleSizeChange(newSize) {
  console.log(params.pageSize)
  params.pageNum = 1
  getQuestionList()
}

function handleCurrentChange(newPage) {
  getQuestionList()
}

function goQuestTest(questionId) {
  router.push(`/c-oj/anwser?questionId=${questionId}`)
}
</script>

<style lang="scss" scoped>
.index-question {
  background-color: #f7f7f7;
  display: flex;
  justify-content: center;
  padding: 20px;
  min-height: calc(100vh - 80px);

  .index-question-table-box {
    display: flex;
    max-width: 1520px;
    width: 100%;
    gap: 20px;
    justify-content: space-between;

    .left {
      width: calc(100% - 360px);
      border-radius: 16px;
      overflow: hidden;
      background: #fff;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
      padding: 24px;

      .search-form {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 16px;
        margin-bottom: 20px;

        .search-input-item {
          flex: 1;
          min-width: 300px;
          margin-bottom: 0;
        }

        .selector-item {
          width: 180px;
          margin-bottom: 0;
        }

        .button-group {
          margin-bottom: 0;
        }

        .keyword-input {
          :deep(.el-input__wrapper) {
            border-radius: 10px;
            height: 44px;
            background: #f8f9fa;
            transition: all 0.3s ease;
            
            &:hover {
              box-shadow: 0 0 0 1px #409EFF;
              background: #fff;
            }
            
            &.is-focus {
              box-shadow: 0 0 0 1px #409EFF;
              background: #fff;
            }
          }
        }

        .difficulty-selector {
          :deep(.el-input__wrapper) {
            border-radius: 10px;
            height: 44px;
            background: #f8f9fa;
          }
        }

        .search-btn {
          border-radius: 10px;
          height: 44px;
          padding: 0 24px;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border: none;
          
          &:hover {
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
          }
          
          .el-icon {
            margin-right: 6px;
          }
        }

        .reset-btn {
          border-radius: 10px;
          height: 44px;
          padding: 0 24px;
          
          &:hover {
            color: #667eea;
            border-color: #667eea;
          }
          
          .el-icon {
            margin-right: 6px;
          }
        }
      }

      .question-table {
        border-radius: 12px;
        overflow: hidden;
        
        :deep(.el-table__header) {
          th {
            background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%) !important;
            color: #333;
            font-weight: 600;
            font-size: 14px;
            border-bottom: 2px solid #e9ecef;
          }
        }
        
        :deep(.el-table__body) {
          tr:hover > td {
            background: rgba(102, 126, 234, 0.04) !important;
          }
        }

        .index-number {
          font-weight: 600;
          color: #667eea;
        }

        .difficulty-tag {
          border: none;
          font-weight: 500;
          padding: 6px 12px;
          
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

        .start-btn {
          border-radius: 8px;
          font-weight: 500;
          
          &:hover {
            transform: translateY(-1px);
            box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
          }
          
          .el-icon {
            margin-right: 4px;
          }
        }

        .login-tip {
          color: #999;
          font-size: 13px;
        }
      }

      .custom-pagination {
        margin-top: 20px;
        display: flex;
        justify-content: flex-end;
        
        :deep(.el-pager li.is-active) {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border-color: #667eea;
        }
      }
    }

    .right {
      width: 340px;
      display: flex;
      flex-direction: column;
      gap: 20px;

      .top-box {
        width: 100%;
        height: auto;
        border-radius: 16px;
        background: #fff;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
        overflow: hidden;
        
        :deep(.el-calendar) {
          background: transparent;
          
          .el-calendar__header {
            border: none;
            padding: 16px 16px 8px;
          }
          
          .el-calendar__title {
            font-weight: 600;
            color: #333;
          }
          
          .el-calendar-table {
            thead th {
              color: #667eea;
              font-weight: 600;
              padding: 8px 0;
            }
            
            .el-calendar-day {
              height: 36px;
              text-align: center;
            }
            
            td {
              border: none;
            }
            
            .el-calendar-day:hover {
              background: rgba(102, 126, 234, 0.1);
            }
          }
        }
      }

      .bot-box {
        width: 100%;
        border-radius: 16px;
        background: #fff;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
        overflow: hidden;

        .title {
          padding: 20px;
          background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
          text-align: center;
          border-bottom: 1px solid #e9ecef;
        }

        .hot-list {
          padding: 20px;
          
          .list-item {
            display: flex;
            align-items: center;
            padding: 12px 0;
            border-bottom: 1px solid #f0f0f0;
            cursor: pointer;
            transition: all 0.3s ease;
            
            &:last-child {
              border-bottom: none;
            }
            
            &:hover {
              background: rgba(102, 126, 234, 0.05);
              transform: translateX(4px);
              
              .txt {
                color: #667eea;
              }
            }

            .index-box {
              width: 24px;
              height: 24px;
              display: flex;
              align-items: center;
              justify-content: center;
              margin-right: 12px;
              
              &.number {
                font-weight: 600;
                color: #999;
                font-size: 14px;
              }
            }

            .txt {
              flex: 1;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
              font-size: 14px;
              color: #333;
              transition: color 0.3s ease;
            }
          }
        }
      }
    }
  }
}

// 响应式设计
@media (max-width: 1200px) {
  .index-question .index-question-table-box {
    flex-direction: column;
    
    .left {
      width: 100%;
    }
    
    .right {
      width: 100%;
      flex-direction: row;
      
      .top-box,
      .bot-box {
        flex: 1;
      }
    }
  }
}

@media (max-width: 768px) {
  .index-question {
    padding: 16px;
    
    .index-question-table-box {
      .left {
        padding: 16px;
        
        .search-form {
          flex-direction: column;
          align-items: stretch;
          
          .search-input-item,
          .selector-item {
            width: 100%;
            min-width: auto;
          }
        }
      }
      
      .right {
        flex-direction: column;
      }
    }
  }
}
</style>