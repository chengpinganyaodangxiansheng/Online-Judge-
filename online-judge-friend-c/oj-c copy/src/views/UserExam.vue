<template>
  <div class="my-exam-page">
    <div class="exam-list-block">
      <div class="exam-list-header">
        <span class="ex-title">我的竞赛</span>
        <span class="exam-list-back" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </span>
      </div>
      <div class="exam-list-content" v-for="(exam, index) in myExamList" :key="index">
        <img src="@/assets/ide/竞赛logo.jpg" class="image" />
        <div class="exam-content">
          <div class="title">
            {{ exam.title }}
          </div>
          <div class="date"><span>比赛时间:</span> {{ exam.startTime }} ~ {{ exam.endTime }}</div>
        </div>
        <div>
          <div class="exam-end-lable-list" v-if="isHistoryExam(exam)">
            <span class="exam-end-lable">已完赛</span>
            <el-button class="exam-rank-lable" type="primary" size="small" @click="togglePopover(exam.examId)">
              <el-icon><Trophy /></el-icon>
              查看排名
            </el-button>
            <el-button class="exam-rank-lable" type="primary" size="small" plain @click="goHistoryExam(exam)">
              <el-icon><Reading /></el-icon>
              竞赛练习
            </el-button>
          </div>
          <div class="exam-status-lable exam-end-lable-list" v-else-if="isNotStart(exam)">
            <el-tag type="info" effect="light">
              <el-icon><Clock /></el-icon>
              未开赛
            </el-tag>
          </div>
          <div class="exam-end-lable-list" v-else>
            <el-button class="exam-rank-lable" type="primary" @click="goExam(exam)">
              <el-icon><Right /></el-icon>
              开始答题
            </el-button>
          </div>
        </div>
      </div>
      <div class="my-exam-pagination">
        <!-- 增加分页展示器 -->
        <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="total"
          v-model:current-page="params.pageNum" v-model:page-size="params.pageSize" :page-sizes="[5, 10, 15, 20]"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import {
  getMyExamListService,
} from "@/apis/exam"
import router from '@/router'
import { ArrowLeft, Trophy, Reading, Clock, Right } from '@element-plus/icons-vue'

const myExamList = ref([]) //消息列表

const total = ref(0)
const params = reactive({
  pageNum: 1,
  pageSize: 10,
})

// 分页
function handleSizeChange(newSize) {
  params.pageNum = 1
  getMyExamList()
}

function handleCurrentChange(newPage) {
  getMyExamList()
}

//消息列表
async function getMyExamList() {
  const ref = await getMyExamListService(params.value)
  myExamList.value = ref.rows
  total.value = ref.total
}
getMyExamList()

function isHistoryExam(exam) {
  const now = new Date();
  return new Date(exam.endTime) < now;
}

const isNotStart = (exam) => {
  const now = new Date(); //当前时间
  return new Date(exam.startTime) > now;
}

const goBack = () => {
  router.go(-1)
}

</script>

<style lang="scss" scoped>
.my-exam-page {
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4efe9 100%);
  min-height: 100vh;
  padding: 20px;
  box-sizing: border-box;
}

.exam-list-block {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.exam-list-header {
  background: #fff;
  border-radius: 16px;
  width: 100%;
  height: 80px;
  display: flex;
  align-items: center;
  padding: 0 30px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.5);

  .ex-title {
    font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
    font-weight: 600;
    font-size: 22px;
    color: #333;
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

  .exam-list-back {
    cursor: pointer;
    color: #666;
    font-size: 14px;
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    border-radius: 6px;
    transition: all 0.3s ease;
    
    &:hover {
      background: rgba(102, 126, 234, 0.1);
      color: #667eea;
    }
    
    .el-icon {
      font-size: 16px;
    }
  }
}

.exam-list-content {
  height: 120px;
  width: 100%;
  background: #fff;
  border-radius: 16px;
  display: flex;
  align-items: center;
  padding: 0 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.5);
  transition: all 0.3s ease;
  cursor: pointer;
  gap: 24px;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
    border-color: rgba(102, 126, 234, 0.2);
  }

  img {
    width: 60px;
    height: 60px;
    border-radius: 12px;
    object-fit: cover;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.exam-list-content .exam-content {
  flex: 1;
  min-width: 0;
}

.exam-list-content .title {
  font-weight: 600;
  font-size: 18px;
  color: #333;
  margin-bottom: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exam-list-content .date {
  font-weight: 400;
  color: #666;
  line-height: 1.5;
  font-size: 14px;

  span {
    color: #999;
    margin-right: 8px;
  }
}

.exam-list-content .exam-status-lable {
  color: #999;
}

.exam-end-lable-list {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: auto;

  .exam-end-lable {
    font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
    font-size: 14px;
    color: #999;
    background: rgba(153, 153, 153, 0.1);
    padding: 6px 12px;
    border-radius: 6px;
  }

  .exam-rank-lable {
    border-radius: 8px;
    padding: 8px 16px;
    font-weight: 500;
    transition: all 0.3s ease;
    
    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
    }
    
    .el-icon {
      margin-right: 6px;
      font-size: 14px;
    }
  }
}

.my-exam-pagination {
  width: 100%;
  display: flex;
  justify-content: center;
  padding: 20px 0;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  margin-top: 20px;
  
  :deep(.el-pagination) {
    .el-pager li.is-active {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-color: #667eea;
    }
  }
}

// 空状态提示
.exam-list-content:empty::before {
  content: '暂无竞赛记录';
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 200px;
  color: #999;
  font-size: 16px;
}

// 响应式设计
@media (max-width: 768px) {
  .my-exam-page {
    padding: 16px;
  }
  
  .exam-list-header {
    padding: 0 20px;
    height: 70px;
    
    .ex-title {
      font-size: 18px;
    }
  }
  
  .exam-list-content {
    padding: 0 20px;
    height: auto;
    flex-direction: column;
    align-items: flex-start;
    padding: 20px;
    gap: 16px;
    
    img {
      width: 50px;
      height: 50px;
    }
    
    .exam-content {
      width: 100%;
    }
    
    .exam-end-lable-list {
      width: 100%;
      justify-content: flex-end;
      margin-top: 8px;
    }
  }
  
  .my-exam-pagination {
    padding: 16px;
  }
}

@media (max-width: 480px) {
  .exam-list-content {
    .exam-end-lable-list {
      flex-wrap: wrap;
      justify-content: center;
      gap: 8px;
      
      .exam-rank-lable {
        flex: 1;
        min-width: 120px;
        justify-content: center;
      }
    }
  }
  
  .my-exam-pagination {
    :deep(.el-pagination) {
      .btn-prev,
      .btn-next,
      .number {
        margin: 0 2px;
      }
    }
  }
}
</style>