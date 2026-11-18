<template>
  <div class="page praticle-page flex-col">
    <div class="header flex-row">
      <div class="header-left flex-row">
        <div class="exam-info">
          <img class="exam-icon" src="@/assets/ide/liebiao.png" />
          <span class="exam-title">{{ examTitle ? examTitle : '精选题库' }}</span>
          <el-countdown v-if="examEndTime && new Date() < new Date(examEndTime)" class="exam-time-countdown"
            @finish="handleCountdownFinish" title="距离竞赛结束还有:" :value="new Date(examEndTime)" />
        </div>
      </div>
      <div class="header-right flex-row">
        <el-button type="primary" class="submit-btn" :loading="submitting" @click="submitQuestion">新提交</el-button>
        <el-button v-if="pendingRequest" :disabled="submitting" @click="sendPendingRequest">重试本次提交</el-button>
        <span class="back-btn" @click="goBack()">返回</span>
      </div>
    </div>
    
    <div class="main-content flex-col">
      <div class="content-header flex-row">
        <div class="question-nav-section flex-row">
          <div class="nav-title">
            <img class="nav-icon" src="@/assets/ide/xiaobiaoti.png" />
            <span>题目描述</span>
          </div>
          <div class="nav-btn" @click="preQuestion">
            <el-icon><ArrowLeft /></el-icon>
            <span>上一题</span>
          </div>
          <div class="nav-btn" @click="nextQuestion">
            <span>下一题</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="code-title-section flex-row">
          <img class="code-icon" src="@/assets/ide/daima.png" />
          <span>代码</span>
        </div>
      </div>
      
      <div class="content-body flex-row">
        <div class="question-panel flex-col">
          <div class="question-header">
            <h2 class="question-title">{{ questionDetail.title }}</h2>
            <div class="question-meta">
              <span v-if="questionDetail.difficulty === 1" class="difficulty easy">简单</span>
              <span v-if="questionDetail.difficulty === 2" class="difficulty medium">中等</span>
              <span v-if="questionDetail.difficulty === 3" class="difficulty hard">困难</span>
              <span class="limit">时间限制: {{ questionDetail.timeLimit }}ms</span>
              <span class="limit">空间限制: {{ questionDetail.spaceLimit }}字节</span>
            </div>
          </div>
          <div class="question-content" v-html="questionDetail.content"></div>
        </div>
        
        <div class="code-panel flex-col">
          <div class="editor-container">
            <codeEditor ref="defaultCodeRef" @update:value="handleEditorContent"></codeEditor>
          </div>
          
          <div class="result-section">
            <div class="result-header flex-row">
              <img class="result-icon" src="@/assets/ide/codeResult.png" />
              <span class="result-title">执行结果</span>
            </div>
            
            <div class="result-content">
              <div class="result-status">
                <div class="status-indicator flex-row">
                  <span class="status-text" 
                    :class="{
                      'status-fail': userQuestionResultVO.pass === 0,
                      'status-success': userQuestionResultVO.pass === 1,
                      'status-waiting': userQuestionResultVO.pass === 2,
                      'status-processing': userQuestionResultVO.pass === 3
                    }">
                    <span v-if="userQuestionResultVO.pass === 0">未通过</span>
                    <span v-if="userQuestionResultVO.pass === 1">通过</span>
                    <span v-if="userQuestionResultVO.pass === 2">请先执行代码</span>
                    <span v-if="userQuestionResultVO.pass === 3">系统正在处理您的代码，请稍后</span>
                  </span>
                </div>
                
                <div v-if="userQuestionResultVO.pass === 0" class="error-info">
                  <div class="error-title">异常信息：</div>
                  <div class="error-message">{{ userQuestionResultVO.exeMessage }}</div>
                </div>
              </div>
              
              <div v-if="userQuestionResultVO.userExeResultList && userQuestionResultVO.userExeResultList.length > 0" 
                   class="test-cases">
                <el-table :data="userQuestionResultVO.userExeResultList" class="result-table">
                  <el-table-column prop="input" label="输入" min-width="120" />
                  <el-table-column prop="output" label="预期结果" min-width="120" />
                  <el-table-column prop="exeOutput" label="实际输出" min-width="120" />
                </el-table>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onUnmounted } from "vue"
import codeEditor from "@/components/CodeEditor.vue"
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useRoute } from "vue-router"
import { getQuestionDetailService, preQuestionService, nextQuestionService, getQuestionResultService } from "@/apis/question"
import router from "@/router"
import { examNextQuestionService, examPreQuestionService, getExamFirstQuestionService } from "@/apis/exam"
import { ElMessage } from "element-plus"
import { userSubmitService } from "@/apis/user"

function goBack() {
  router.go(-1);
}
const questionDetail = reactive({})
const defaultCodeRef = ref()

let questionId = useRoute().query.questionId
let examId = useRoute().query.examId
let examTitle = useRoute().query.examTitle
let examEndTime = useRoute().query.examEndTime

console.log('examTitle: ', examTitle)

async function getQuestionDetail() {
  if (examId && (questionId == null || questionId == '')) {
    const eqrs = await getExamFirstQuestionService(examId)
    questionId = eqrs.data
    console.log('qId: ', questionId)
  }
  const res = await getQuestionDetailService(questionId, examId || undefined)
  Object.assign(questionDetail, res.data)
  defaultCodeRef.value.setAceCode(questionDetail.defaultCode)
}
getQuestionDetail()

async function preQuestion() {
  if (examId) {
    //竞赛中上一题的逻辑  需要提供一个竞赛中获取上一题的接口
    const res = await examPreQuestionService(examId, questionId)
    questionId = res.data
  } else {
    const res = await preQuestionService(questionId)
    questionId = res.data
  }
  getQuestionDetail()
}

async function nextQuestion() {
  if (examId) {
    //竞赛中下一题的逻辑 需要提供一个竞赛中获取下一题的接口
    const res = await examNextQuestionService(examId, questionId)
    questionId = res.data
  } else {
    const res = await nextQuestionService(questionId)
    questionId = res.data
  }
  getQuestionDetail()
}

function handleCountdownFinish() {
  ElMessage.info('竞赛已经结束了哦')
  router.push('/c-oj/home/exam')
}

const submitDTO = reactive({
  examId:'',
  questionId:'',
  programType: 0,
  userCode: ''
})

function handleEditorContent(content) {
  submitDTO.userCode = content
}

const userQuestionResultVO = ref({
  pass: 2,  //默认值为2，未提交代码
  exeMessage: '',
  userExeResultList: [],
})

const pollingInterval = ref(null);
const submitting = ref(false)
// 未确认受理的原始请求；即使编辑器继续修改，重试也必须复用原代码和原幂等键。
const pendingRequest = ref(null)
// 已获服务端确认的任务编号，后续只查询该任务，不通过时间或题目猜测结果归属。
const acceptedSubmitId = ref(null)

function startPolling() {
  stopPolling(); // 停止之前的轮询
  pollingInterval.value = setInterval(() => {
    getQuestionResult();
  }, 2000); // 每隔2秒请求一次
}

function stopPolling() {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value);
    pollingInterval.value = null;
  }
}

// 离开页面释放定时器，避免继续发起后台轮询。
onUnmounted(stopPolling)

async function submitQuestion() {
  if (submitting.value) return
  // 主动“新提交”才生成新键；冻结这一刻的题目、版本与代码作为网络重试依据。
  pendingRequest.value = Object.freeze({
    examId: examId || null,
    questionId,
    questionVersion: questionDetail.questionVersion,
    programType: submitDTO.programType,
    userCode: submitDTO.userCode,
    idempotencyKey: crypto.randomUUID()
  })
  await sendPendingRequest()
}

// “重试本次提交”只重发保存的请求，不重新读取编辑器，也不重新生成幂等键。
async function sendPendingRequest() {
  // 请求正在发送 OR 没有待确认的原始请求时都直接退出，防止重复点击或空请求。
  if (submitting.value || !pendingRequest.value) return
  submitting.value = true
  try {
    const res = await userSubmitService(pendingRequest.value)
    // 网络成功不等于业务已确认；必须拿到任务编号，缺少 data 或 submitId 都保留原请求供重试。
    if (!res.data?.submitId) throw new Error('提交未确认')
    // 得到任务编号才清除待确认请求；接口超时不能推断服务器没有受理。
    acceptedSubmitId.value = res.data.submitId
    pendingRequest.value = null
    userQuestionResultVO.value = { pass: 3, exeMessage: '' }
    startPolling()
  } catch (error) {
    // 失败时不清空 pendingRequest，否则响应丢失后用户只能生成新键，可能重复创建已受理任务。
    ElMessage.warning('未确认是否受理，请重试本次提交。新提交可能产生另一条任务。')
  } finally {
    submitting.value = false
  }
}

async function getQuestionResult() {
  const id = acceptedSubmitId.value
  try {
    const res = await getQuestionResultService(id)
    // 旧任务的慢响应不能覆盖用户后续新提交的结果。
    if (id !== acceptedSubmitId.value) return
    // FINISHED 包含 AC/WA/CE 等所有有效判题结果；SYSTEM_FAILED 单独显示系统失败原因。
    if (res.data.state === 'FINISHED') {
      userQuestionResultVO.value = res.data.result
      stopPolling()
    } else if (res.data.state === 'SYSTEM_FAILED') {
      userQuestionResultVO.value = { pass: 0, exeMessage: `系统失败：${res.data.failureReason}` }
      stopPolling()
    }
  } catch (error) {
    // 查询失败不改变已受理状态，下次轮询继续查询同一 submitId。
  }
}
</script>

<style lang="scss" scoped>
.praticle-page {
  background-color: #f5f7fa;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  
  .header {
    background: linear-gradient(135deg, #1e2a47 0%, #2c3e50 100%);
    height: 60px;
    padding: 0 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    
    .header-left {
      display: flex;
      align-items: center;
      
      .exam-info {
        display: flex;
        align-items: center;
        gap: 12px;
        
        .exam-icon {
          width: 24px;
          height: 24px;
          filter: brightness(0) invert(1);
        }
        
        .exam-title {
          color: white;
          font-size: 18px;
          font-weight: 600;
        }
        
        .exam-time-countdown {
          color: #ffcc00;
          font-weight: 500;
          margin-left: 24px;
        }
      }
    }
    
    .header-right {
      display: flex;
      align-items: center;
      gap: 20px;
      
      .submit-btn {
        height: 36px;
        font-weight: 500;
        box-shadow: 0 2px 6px rgba(0, 123, 255, 0.3);
      }
      
      .back-btn {
        color: #c8d6e5;
        cursor: pointer;
        padding: 8px 12px;
        border-radius: 4px;
        transition: all 0.3s;
        
        &:hover {
          color: white;
          background-color: rgba(255, 255, 255, 0.1);
        }
      }
    }
  }
  
  .main-content {
    flex: 1;
    padding: 16px;
    overflow: hidden;
    
    .content-header {
      height: 50px;
      margin-bottom: 16px;
      
      .question-nav-section {
        background: white;
        border-radius: 8px 8px 0 0;
        height: 100%;
        width: 600px;
        display: flex;
        align-items: center;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
        
        .nav-title {
          display: flex;
          align-items: center;
          padding: 0 20px;
          font-weight: 500;
          color: #2c3e50;
          border-right: 1px solid #eee;
          height: 100%;
          
          .nav-icon {
            width: 16px;
            height: 16px;
            margin-right: 8px;
          }
        }
        
        .nav-btn {
          display: flex;
          align-items: center;
          padding: 0 16px;
          height: 100%;
          cursor: pointer;
          transition: all 0.2s;
          color: #5a6c7d;
          font-weight: 500;
          
          &:hover {
            background-color: #f8f9fa;
            color: #3498db;
          }
          
          .el-icon {
            margin: 0 4px;
          }
        }
      }
      
      .code-title-section {
        background: white;
        border-radius: 8px 8px 0 0;
        height: 100%;
        flex: 1;
        display: flex;
        align-items: center;
        padding: 0 20px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
        font-weight: 500;
        color: #2c3e50;
        
        .code-icon {
          width: 20px;
          height: 16px;
          margin-right: 8px;
        }
      }
    }
    
    .content-body {
      height: calc(100% - 66px);
      gap: 16px;
      
      .question-panel {
        background: white;
        border-radius: 0 0 8px 8px;
        width: 600px;
        padding: 20px;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
        overflow-y: auto;
        
        .question-header {
          margin-bottom: 20px;
          padding-bottom: 16px;
          border-bottom: 1px solid #eee;
          
          .question-title {
            color: #2c3e50;
            margin: 0 0 12px 0;
            font-size: 24px;
            font-weight: 600;
          }
          
          .question-meta {
            display: flex;
            align-items: center;
            gap: 16px;
            flex-wrap: wrap;
            
            .difficulty {
              padding: 4px 12px;
              border-radius: 12px;
              font-size: 12px;
              font-weight: 500;
              
              &.easy {
                background-color: #e8f5e9;
                color: #2e7d32;
              }
              
              &.medium {
                background-color: #fff3e0;
                color: #ef6c00;
              }
              
              &.hard {
                background-color: #ffebee;
                color: #c62828;
              }
            }
            
            .limit {
              color: #7f8c8d;
              font-size: 14px;
            }
          }
        }
        
        .question-content {
          color: #34495e;
          line-height: 1.6;
          font-size: 15px;
          
          :deep(*) {
            margin-bottom: 12px;
          }
          
          :deep(pre) {
            background: #f8f9fa;
            padding: 12px;
            border-radius: 4px;
            overflow-x: auto;
          }
          
          :deep(code) {
            background: #f1f3f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
            font-size: 14px;
          }
        }
      }
      
      .code-panel {
        flex: 1;
        border-radius: 0 0 8px 8px;
        overflow: hidden;
        display: flex;
        flex-direction: column;
        
        .editor-container {
          flex: 1;
          background: white;
          border-radius: 0 0 8px 8px;
          overflow: hidden;
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
        }
        
        .result-section {
          margin-top: 16px;
          background: white;
          border-radius: 8px;
          overflow: hidden;
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
          
          .result-header {
            background: #f8f9fa;
            height: 50px;
            padding: 0 20px;
            display: flex;
            align-items: center;
            border-bottom: 1px solid #eee;
            
            .result-icon {
              width: 20px;
              height: 20px;
              margin-right: 8px;
            }
            
            .result-title {
              font-weight: 500;
              color: #2c3e50;
            }
          }
          
          .result-content {
            padding: 20px;
            
            .result-status {
              margin-bottom: 20px;
              
              .status-indicator {
                margin-bottom: 12px;
                
                .status-text {
                  font-weight: 500;
                  font-size: 16px;
                  padding: 6px 12px;
                  border-radius: 4px;
                  
                  &.status-fail {
                    color: #e74c3c;
                    background-color: #ffeaea;
                  }
                  
                  &.status-success {
                    color: #27ae60;
                    background-color: #eafaf1;
                  }
                  
                  &.status-waiting {
                    color: #f39c12;
                    background-color: #fef5e7;
                  }
                  
                  &.status-processing {
                    color: #3498db;
                    background-color: #e8f4fd;
                  }
                }
              }
              
              .error-info {
                background: #fef5f5;
                border: 1px solid #fadbd8;
                border-radius: 4px;
                padding: 12px;
                
                .error-title {
                  font-weight: 500;
                  color: #c0392b;
                  margin-bottom: 4px;
                }
                
                .error-message {
                  color: #7b7b7b;
                  font-family: monospace;
                  white-space: pre-wrap;
                  word-break: break-all;
                }
              }
            }
            
            .test-cases {
              .result-table {
                width: 100%;
                
                :deep(.el-table__header) {
                  th {
                    background-color: #f8f9fa;
                    color: #2c3e50;
                    font-weight: 600;
                  }
                }
                
                :deep(.el-table__row) {
                  td {
                    font-family: monospace;
                    font-size: 13px;
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

.flex-col {
  display: flex;
  flex-direction: column;
}

.flex-row {
  display: flex;
  flex-direction: row;
}

.justify-between {
  justify-content: space-between;
}

// 响应式调整
@media (max-width: 1200px) {
  .content-body {
    flex-direction: column;
    
    .question-panel, .code-panel {
      width: 100% !important;
    }
    
    .question-panel {
      max-height: 40vh;
    }
  }
}
</style>
