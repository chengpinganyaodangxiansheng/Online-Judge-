<template>
  <div class="exam-page flex-col">
    <div class="exam-selected-section flex-col">
      <div class="exam-option-group flex-row justify-between">
        <div class="exam-option" v-for="option in options" :key="option.value" @click="selectOption(option.value)"
          :class="{ selected: selectedOption === option.value }">
          {{ option.label }}
        </div>
      </div>
      <div class="center-box">
        <span class="exam-list-title">推荐竞赛</span>
        <el-form inline="true" class="exam-navigation flex-row justify-between">
          <el-form-item label="竞赛时间" prop="datetimerange" class="exam-navigation-box">
            <el-date-picker style="width:360px;" v-model="params.datetimerange" type="datetimerange" range-separator="至"
              start-placeholder="开始时间" end-placeholder="结束时间"></el-date-picker>
          </el-form-item>
          <el-form-item>
            <el-button @click="onSearch" plain type="primary">搜索</el-button>
            <el-button @click="onReset" plain type="info">重置</el-button>
          </el-form-item>
        </el-form>
        <!-- </div> -->
        <div class="exam-list-group flex-row">
          <div class="exam-list-item flex-col" v-for="(exam, index) in examList" :key="index">
            <div>
              <img src="@/assets/images/竞赛.webp">
            </div>
            <div class="right-info">
              <span class="exam-title">{{ exam.title }}</span>
              <div class="exam-content flex-col justify-between">
                <span>开赛时间：{{ exam.startTime }}</span>
                <span>结束时间：{{ exam.endTime }}</span>
              </div>
              <div class="exam-button-container">
                <span class="exam-hash-entry" v-if="isOngoingAndUnregisteredCompetition(exam)">已开赛</span>
                <span class="exam-hash-entry" v-if="isEntryAndNotStart(exam)">已报名</span>
                <div v-if="isHistoryExam(exam)">
                  <el-button class="exam-practice-button" type="primary" plain
                    @click="goExam(exam)">竞赛练习</el-button>
                  <el-button class="exam-rank-button" type="primary" plain
                    @click="togglePopover(exam.examId)">查看排名</el-button>
                </div>
                <el-button class="exam-start-button" type="primary" plain v-if="isStartExam(exam)"
                  @click="goExam(exam)">开始答题</el-button>
                <el-button class="exam-enter-button" type="primary" plain v-if="isCanEntry(exam)"
                  @click="enterExam(exam.examId)">报名参赛</el-button>
              </div>
            </div>
          </div>
        </div>
        <div class="exam-page-pagination flex-row">
          <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="total"
            v-model:current-page="params.pageNum" v-model:page-size="params.pageSize" :page-sizes="[3, 6, 9, 12]"
            @size-change="handleSizeChange" @current-change="handleCurrentChange" />
        </div>
      </div>
    </div>
  </div>

  <el-dialog v-model="dialogVisible" width="600px" top="30vh" :show-close="true" :close-on-click-modal="false"
    :close-on-press-escape="false" class="oj-login-dialog-centor" center>
    <el-table :data="examRankList">
      <el-table-column label="排名" prop="examRank" />
      <el-table-column label="用户昵称" prop="nickName" />
      <el-table-column label="用户得分" prop="score" />
    </el-table>
    <el-pagination class="range_page" background layout="total, sizes, prev, pager, next, jumper" :total="rankTotal"
      v-model:current-page="rankParams.pageNum" v-model:page-size="rankParams.pageSize" :page-sizes="[5, 10, 15, 20]"
      @size-change="handleRankSizeChange" @current-change="handleRankCurrentChange" />
  </el-dialog>

</template>

<script setup>
import { reactive, ref } from 'vue'
import { getExamListService,enterExamService, getExamRankListService } from '@/apis/exam'
import { getToken } from '@/utils/cookie'
import { getUserInfoService } from '@/apis/user'
import router from '@/router';

//参数定义
const examList = ref([]) //题目列表
const total = ref(0)
const selectedOption = ref(0); // 初始化选中的文本
const options = ref([
  { label: '未完赛', value: 0 },
  { label: '历史竞赛', value: 1 },
])
const params = reactive({
  pageNum: 1,
  pageSize: 9,
  type: 0
})

//竞赛列表
async function getExamList() {
  const result = await getExamListService(params)
  examList.value = result.rows
  total.value = result.total
}
getExamList()

async function selectOption(type) {
  selectedOption.value = type
  params.pageNum = 1
  params.type = type
  getExamList()
}

// 搜索/重置
function onSearch() {
  params.value.pageNum = 1
  getExamList()
}

function onReset() {
  params.pageNum = 1
  params.pageSize = 9
  params.type = 0
  getExamList()
}

// 分页
function handleSizeChange(newSize) {
  params.pageNum = 1
  getExamList()
}

function handleCurrentChange(newPage) {
  getExamList()
}

function isOngoingAndUnregisteredCompetition(exam){
  const now = new Date(); //当前时间
  return new Date(exam.startTime) < now && new Date(exam.endTime) > now && !exam.enter
}

function isEntryAndNotStart(exam) {
  const now = new Date();
  return new Date(exam.startTime) > now && exam.enter
}

function isHistoryExam(exam) {
  const now = new Date();
  return new Date(exam.endTime) < now;
}

function isStartExam(exam) {
  const now = new Date();
  return new Date(exam.startTime) < now && new Date(exam.endTime) > now && exam.enter;
}

function isCanEntry (exam) {
  const now = new Date();
  return new Date(exam.startTime) > now && !exam.enter;
}

const isLogin = ref(false)
async function checkLogin() {
  if (getToken()) {
    await getUserInfoService()
    isLogin.value = true
  }
}

async function enterExam(examId) {
  await checkLogin()
  if (!isLogin.value) {
    ElMessage.error('请先登录后报名参赛,谢谢')
    return
  }
  const enterExamDTO = ref({
    examId: examId
  })
  await enterExamService(enterExamDTO.value)
  ElMessage.success('您已报名成功，请按时参赛',)
  getExamList() //报名成功后刷新竞赛列表
}

function goExam(exam) {
  //需要后端提供一个获取竞赛中第一道题目的id接口
  router.push(`/c-oj/anwser?examId=${exam.examId}&examTitle=${exam.title}&examEndTime=${exam.endTime}`)
}

const rankParams = reactive({
  examId:'',
  pageNum: 1,
  pageSize: 9,
})
const examRankList = ref([])
const rankTotal = ref(0)

// 分页
function handleRankSizeChange(newSize) {
  rankParams.pageNum = 1
  getExamRankList()
}

function handleRankCurrentChange(newPage) {
  getExamRankList()
}

const dialogVisible = ref(false)

async function getExamRankList() {
  const result = await getExamRankListService(rankParams)
  examRankList.value = result.rows
  rankTotal.value = result.total
}

function togglePopover(examId) {
  dialogVisible.value = true
  rankParams.examId = examId
  getExamRankList()
}

</script>

<style lang="scss" scoped>
.exam-page {
  background: linear-gradient(135deg, #f5f7fa 0%, #e4efe9 100%);
  position: relative;
  overflow: hidden;
  display: flex;
  min-height: 100vh;
  padding: 20px;

  .center-box {
    max-width: 1520px;
    margin: 0 auto;
    width: 100%;
    min-height: 368px;
    background: #FFFFFF;
    border-radius: 16px;
    padding: 30px;
    margin-top: 10px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  }

  .exam-selected-section {
    margin: 0 auto;
    margin-bottom: 20px;
    position: relative;
    padding-top: 50px;
    max-width: 1520px;
    width: 100%;

    .exam-option-group {
      width: fit-content;
      height: 50px;
      position: absolute;
      top: 0;
      left: 0;
      background: #fff;
      border-radius: 12px;
      padding: 4px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

      .exam-option {
        cursor: pointer;
        padding: 10px 24px;
        border-radius: 8px;
        transition: all 0.3s ease;
        font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
        font-weight: 600;
        font-size: 16px;
        color: #666;
        height: 42px;
        width: fit-content;
        display: flex;
        justify-content: center;
        align-items: center;
        margin-right: 4px;

        &:last-child {
          margin-right: 0;
        }

        &:hover {
          color: #667eea;
          background: rgba(102, 126, 234, 0.05);
        }
      }

      .exam-option.selected {
        color: #fff;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
      }
    }

    .exam-list-title {
      height: 24px;
      font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
      font-weight: 600;
      font-size: 20px;
      color: #333;
      line-height: 25px;
      text-align: left;
      margin-bottom: 30px;
      display: block;
      position: relative;
      padding-left: 16px;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 4px;
        height: 20px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-radius: 2px;
      }
    }

    :deep(.exam-navigation) {
      width: 100%;
      height: auto;
      margin-bottom: 30px;
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;

      .el-form-item {
        margin-right: 0;
        margin-bottom: 0;
      }

      .el-form-item__label {
        font-weight: 600;
        color: #333;
        font-size: 14px;
      }

      .exam-navigation-box {
        :deep(.el-input__wrapper) {
          border-radius: 10px;
          transition: all 0.3s ease;
          
          &:hover {
            box-shadow: 0 0 0 1px #409EFF;
          }
          
          &.is-focus {
            box-shadow: 0 0 0 1px #409EFF;
          }
        }
      }

      .el-button {
        border-radius: 8px;
        padding: 10px 20px;
        font-weight: 500;
        
        &.el-button--primary {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border: none;
          
          &:hover {
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
          }
        }
        
        &.el-button--info {
          &:hover {
            color: #667eea;
            border-color: #667eea;
          }
        }
      }
    }

    .exam-list-group {
      flex-wrap: wrap;
      gap: 20px;

      @media screen and (min-width: 1420px) {
        .exam-list-item {
          width: calc(33.333% - 14px);
        }
      }

      @media screen and (max-width: 1419px) {
        .exam-list-item {
          width: calc(50% - 10px);
        }
      }

      @media screen and (max-width: 970px) {
        .exam-list-item {
          width: 100%;
        }
      }

      .exam-list-item {
        height: 220px;
        background: #fff;
        border-radius: 16px;
        padding: 24px;
        box-sizing: border-box;
        display: flex;
        align-items: center;
        flex-direction: row;
        justify-content: space-between;
        cursor: pointer;
        transition: all 0.3s ease;
        border: 1px solid #f0f0f0;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);

        &:hover {
          transform: translateY(-4px);
          box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
          border-color: rgba(102, 126, 234, 0.2);

          .exam-title {
            color: #667eea;
          }
        }

        .right-info {
          width: calc(100% - 146px);
        }

        .exam-title {
          height: 26px;
          font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
          font-weight: 600;
          font-size: 18px;
          color: #333;
          line-height: 26px;
          text-align: left;
          max-width: 90%;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          margin-bottom: 16px;
          display: block;
          transition: color 0.3s ease;
        }

        .exam-content {
          margin-bottom: 20px;

          span {
            height: 22px;
            font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
            font-weight: 400;
            font-size: 14px;
            color: #666;
            line-height: 22px;
            display: block;
            margin-bottom: 8px;
          }
        }

        img {
          width: 126px;
          height: 180px;
          border-radius: 12px;
          object-fit: cover;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }

        .exam-hash-entry {
          float: right;
          font-size: 16px;
          font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
          font-weight: 500;
          color: #999;
          padding: 6px 12px;
          background: rgba(153, 153, 153, 0.1);
          border-radius: 6px;
        }

        .exam-button-container {
          display: flex;
          justify-content: flex-start;
          align-items: center;
          gap: 12px;
          flex-wrap: wrap;

          .el-button {
            height: 36px;
            border-radius: 8px;
            font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
            font-weight: 500;
            font-size: 14px;
            border: 1px solid #667eea;
            color: #667eea;
            padding: 0 16px;
            transition: all 0.3s ease;

            &:hover {
              background: #667eea;
              color: #fff;
              transform: translateY(-1px);
              box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
            }
          }
        }
      }
    }

    .exam-page-pagination {
      width: 100%;
      height: 40px;
      margin: 40px 0 20px 0;
      display: flex;
      justify-content: center;
      
      :deep(.el-pagination) {
        .el-pager li.is-active {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border-color: #667eea;
        }
      }
    }
  }
}

// 对话框样式优化
:deep(.oj-login-dialog-centor) {
  .el-dialog {
    border-radius: 16px;
    overflow: hidden;
    
    .el-dialog__header {
      background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
      margin: 0;
      padding: 16px 20px;
      border-bottom: 1px solid #e9ecef;
      
      .el-dialog__title {
        font-weight: 600;
        color: #333;
      }
    }
    
    .el-table {
      border-radius: 0;
      
      :deep(.el-table__header) {
        th {
          background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
          color: #333;
          font-weight: 600;
        }
      }
    }
    
    .range_page {
      padding: 16px;
      display: flex;
      justify-content: center;
      
      :deep(.el-pager li.is-active) {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-color: #667eea;
      }
    }
  }
}

// 响应式设计
@media (max-width: 768px) {
  .exam-page {
    padding: 16px;
    
    .center-box {
      padding: 20px;
    }
    
    .exam-selected-section {
      padding-top: 70px;
      
      .exam-option-group {
        width: 100%;
        justify-content: center;
        
        .exam-option {
          flex: 1;
          text-align: center;
        }
      }
      
      :deep(.exam-navigation) {
        flex-direction: column;
        align-items: stretch;
        
        .exam-navigation-box {
          width: 100%;
          
          :deep(.el-date-editor) {
            width: 100% !important;
          }
        }
        
        .el-form-item:last-child {
          display: flex;
          justify-content: center;
          gap: 12px;
        }
      }
      
      .exam-list-group .exam-list-item {
        flex-direction: column;
        height: auto;
        text-align: center;
        
        img {
          margin-bottom: 16px;
        }
        
        .right-info {
          width: 100%;
        }
        
        .exam-button-container {
          justify-content: center;
        }
      }
    }
  }
}
</style>