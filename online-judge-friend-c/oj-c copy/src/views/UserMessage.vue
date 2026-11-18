<template>
  <div class="message-list">
    <div class="message-list-block">
      <div class="message-list-header">
        <span class="ms-title">我的消息</span>
        <span class="message-list-back" @click="goBack()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </span>
      </div>
      <div class="mesage-list-content" v-for="(item, index) in messageList" :key="index">
        <div class="message-icon">
          <img src="@/assets/message/消息logo.png" class="image" />
        </div>
        <div class="message-content">
          <div class="title-box">
            <div class="title">
              {{ item.messageTitle }}
            </div>
            <div class="time" v-if="item.createTime">{{ formatTime(item.createTime) }}</div>
          </div>
          <div class="content">{{ item.messageContent }}</div>
        </div>
        <el-button class="mesage-button" type="danger" size="small" @click.stop="handlerDelete(item)">
          <el-icon><Delete /></el-icon>
          删除
        </el-button>
      </div>
      <div class="message-pagination">
        <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="total"
          v-model:current-page="params.pageNum" v-model:page-size="params.pageSize" :page-sizes="[5, 10, 15, 20]"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { getMessageListService } from "@/apis/message"
import router from "@/router"
import { reactive, ref } from "vue"
import { ArrowLeft, Delete } from '@element-plus/icons-vue'

const messageList = ref([]) //消息列表

const total = ref(0)
const params = reactive({
  pageNum: 1,
  pageSize: 10,
})

//消息列表
async function getMessageList() {
  const ref = await getMessageListService(params)
  messageList.value = ref.rows
  total.value = ref.total
}
getMessageList()

const goBack = () => {
  router.go(-1)
}

// 分页
function handleSizeChange(newSize) {
  params.pageNum = 1
  getMessageList()
}

function handleCurrentChange(newPage) {
  getMessageList()
}

// 格式化时间显示
function formatTime(timeString) {
  if (!timeString) return ''
  const date = new Date(timeString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style lang="scss" scoped>
.message-list {
  background: linear-gradient(135deg, #f5f7fa 0%, #e4efe9 100%);
  min-height: 100vh;
  padding: 20px;
  display: flex;
  justify-content: center;

  .message-list-block {
    width: 100%;
    max-width: 1200px;
    display: flex;
    flex-direction: column;
    gap: 20px;

    .message-list-header {
      background: #fff;
      border-radius: 16px;
      width: 100%;
      padding: 0 30px;
      height: 80px;
      display: flex;
      align-items: center;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.5);

      .ms-title {
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

      .message-list-back {
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

    .mesage-list-content {
      background: #fff;
      border-radius: 16px;
      display: flex;
      align-items: flex-start;
      padding: 24px 30px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.5);
      transition: all 0.3s ease;
      gap: 20px;

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
        border-color: rgba(102, 126, 234, 0.2);
      }

      .message-icon {
        flex-shrink: 0;
        display: flex;
        align-items: flex-start;
        
        .image {
          width: 50px;
          height: 50px;
          border-radius: 12px;
          object-fit: cover;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }
      }

      .message-content {
        flex: 1;
        min-width: 0;
        
        .title-box {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 12px;
          gap: 16px;

          .title {
            font-weight: 600;
            font-size: 18px;
            color: #333;
            line-height: 1.4;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .time {
            font-size: 12px;
            color: #999;
            flex-shrink: 0;
            background: rgba(153, 153, 153, 0.1);
            padding: 4px 8px;
            border-radius: 4px;
          }
        }

        .content {
          font-size: 14px;
          color: #666;
          line-height: 1.6;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .mesage-button {
        flex-shrink: 0;
        border-radius: 8px;
        padding: 8px 16px;
        font-weight: 500;
        transition: all 0.3s ease;
        
        &:hover {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
        }
        
        .el-icon {
          margin-right: 6px;
          font-size: 14px;
        }
      }
    }

    .message-pagination {
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
  }
}

// 空状态提示
.mesage-list-content:empty::before {
  content: '暂无消息';
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 200px;
  color: #999;
  font-size: 16px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

// 响应式设计
@media (max-width: 768px) {
  .message-list {
    padding: 16px;
    
    .message-list-block {
      .message-list-header {
        padding: 0 20px;
        height: 70px;
        
        .ms-title {
          font-size: 18px;
        }
      }
      
      .mesage-list-content {
        padding: 20px;
        flex-direction: column;
        align-items: stretch;
        gap: 16px;
        
        .message-icon {
          align-self: flex-start;
        }
        
        .message-content {
          .title-box {
            flex-direction: column;
            align-items: flex-start;
            gap: 8px;
            
            .time {
              align-self: flex-start;
            }
          }
        }
        
        .mesage-button {
          align-self: flex-end;
          margin-top: 8px;
        }
      }
      
      .message-pagination {
        padding: 16px;
      }
    }
  }
}

@media (max-width: 480px) {
  .message-list .message-list-block {
    .mesage-list-content {
      .message-content {
        .title-box {
          .title {
            font-size: 16px;
          }
        }
        
        .content {
          font-size: 13px;
        }
      }
      
      .mesage-button {
        width: 100%;
        justify-content: center;
      }
    }
    
    .message-pagination {
      :deep(.el-pagination) {
        .btn-prev,
        .btn-next,
        .number {
          margin: 0 2px;
        }
      }
    }
  }
}
</style>