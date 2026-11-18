<template>
  <div class="user-info-page">
    <div class="oj-user-info oj-user-info-page">
      <div class="oj-user-info-body">
        <el-form :model="userDetailForm" label-position="top" status-icon label-width="100px" class="demo-ruleForm">
          <div class="oj-user-info-subtitle-box">
            <div class="oj-user-info-subtitle">
              <span class="oj-user-info-dot">
                基本信息
              </span>
              <div class="opt-box">
                <el-button class="user-info-edit" v-if="isDisabled()" type="primary" @click="changeState('updating')">
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-button class="user-info-edit" v-if="!isDisabled()" plain @click="changeState('normal')">
                  <el-icon><Close /></el-icon>
                  取消
                </el-button>
                <el-button class="user-info-back" plain @click="goBack()">
                  <el-icon><ArrowLeft /></el-icon>
                  返回
                </el-button>
              </div>
            </div>
          </div>
          <div class="ku-user-section">
            <el-form-item prop="nickName">
              <div class="photo-image-box">
                <div class="photo-image">
                  <div class="left">
                    <img width="100px" v-if="!userDetailForm.headImage" height="100px" src="@/assets/user/head_image.png">
                    <img width="100px" v-else height="100px" :src="userDetailForm.headImage" class="user-avatar">
                  </div>
                  <div class="right" v-if="!userDetailForm.headImage">
                    <div class="bold">上传你的个人头像</div>
                    <div class="normal">上传你的个性头像展示你的个人风格</div>
                  </div>
                  <div class="right" v-else>
                    <div class="bold">{{ userDetailForm.nickName || '用户' }}</div>
                    <div class="normal">欢迎来到个人中心</div>
                  </div>
                </div>
                <div v-if="!isDisabled()" class="upload-btn-container">
                  <el-upload :action="uploadUrl" :headers="headers"
                    :on-error="handleUploadError" :on-success="handleUploadSuccess" :show-file-list="false">
                    <el-button type="primary">
                      <el-icon><Upload /></el-icon>
                      {{ userDetailForm.headImage ? '更换头像' : '上传头像' }}
                    </el-button>
                    <template #tip> </template>
                  </el-upload>
                </div>
              </div>
            </el-form-item>
            
            <div class="form-grid">
              <el-form-item label="昵称" prop="nickName">
                <el-input v-if="!isDisabled()" type="text" v-model="userDetailForm.nickName" autocomplete="off"
                  placeholder="请填写您的昵称" maxlength="8" class="custom-input"></el-input>
                <span :class="userDetailForm.nickName ? 'right-info' : 'right-info gray'" v-else>
                  {{ userDetailForm.nickName || "请填写您的昵称" }}
                </span>
              </el-form-item>

              <el-form-item label="性别" prop="sex">
                <el-radio-group v-model="userDetailForm.sex" v-if="!isDisabled()" class="gender-radio">
                  <el-radio :label="1">男</el-radio>
                  <el-radio :label="2">女</el-radio>
                </el-radio-group>
                <span :class="userDetailForm.sex ? 'right-info' : 'right-info gray'" v-else>
                  {{ userDetailForm.sex == null ? "请选择您的性别" : +userDetailForm.sex == 1 ? "男" : "女" }}
                </span>
              </el-form-item>

              <el-form-item label="学校" prop="schoolName">
                <el-input v-if="!isDisabled()" type="text" v-model="userDetailForm.schoolName" autocomplete="off"
                  placeholder="请填写您的学校名称" class="custom-input"></el-input>
                <span :class="userDetailForm.schoolName ? 'right-info' : 'right-info gray'" v-else>
                  {{ userDetailForm.schoolName || "请填写您的学校名称" }}
                </span>
              </el-form-item>

              <el-form-item label="专业" prop="majorName">
                <el-input v-if="!isDisabled()" type="text" v-model="userDetailForm.majorName" autocomplete="off"
                  placeholder="请填写您的专业名称" class="custom-input"></el-input>
                <span :class="userDetailForm.majorName ? 'right-info' : 'right-info gray'" v-else>
                  {{ userDetailForm.majorName || "请填写您的专业名称" }}
                </span>
              </el-form-item>

              <el-form-item label="手机" prop="phone">
                <el-input v-if="!isDisabled()" type="text" v-model="userDetailForm.phone" autocomplete="off"
                  placeholder="请填写常用手机号" class="custom-input"></el-input>
                <span :class="userDetailForm.phone ? 'right-info' : 'right-info gray'" v-else>
                  {{ userDetailForm.phone || "请填写常用手机号" }}
                </span>
              </el-form-item>

              <el-form-item label="常用邮箱" prop="email">
                <el-input v-if="!isDisabled()" type="text" v-model="userDetailForm.email" autocomplete="off"
                  placeholder="请填写常用邮箱" class="custom-input"></el-input>
                <span :class="userDetailForm.email ? 'right-info' : 'right-info gray'" v-else>
                  {{ userDetailForm.email || "请填写常用邮箱" }}
                </span>
              </el-form-item>

              <el-form-item label="微信号码" prop="wechat">
                <el-input v-if="!isDisabled()" type="text" v-model="userDetailForm.wechat" autocomplete="off"
                  placeholder="请填写微信号码" class="custom-input"></el-input>
                <span :class="userDetailForm.wechat ? 'right-info' : 'right-info gray'" v-else>
                  {{ userDetailForm.wechat || "请填写微信号" }}
                </span>
              </el-form-item>
            </div>

            <el-form-item label="个人介绍" prop="introduce">
              <el-input v-if="!isDisabled()" :rows="4" type="textarea" v-model="userDetailForm.introduce"
                autocomplete="off" placeholder="请填写个人介绍" maxlength="80" show-word-limit 
                class="custom-textarea"></el-input>
              <span :class="userDetailForm.introduce ? 'right-info intro' : 'right-info intro gray'" v-else>
                {{ userDetailForm.introduce || "请用一段话介绍自己,该简介将会在您的个人主页展示" }}
              </span>
            </el-form-item>
          </div>
          <div class="ku-login-footer" v-if="!isDisabled()">
            <el-button class="ku-login-footer-btn" type="info" plain @click="changeState('normal')">
              <el-icon><Close /></el-icon>
              取消
            </el-button>
            <el-button class="ku-login-footer-btn blue" type="primary" @click="editUser()">
              <el-icon><Check /></el-icon>
              保存
            </el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { getUserDetailService,editUserService, updateHeadImageService } from '@/apis/user'
import { getToken } from '@/utils/cookie'
import router from '@/router'
import { ElMessage } from 'element-plus';
import { reactive, ref } from "vue"
import { Edit, Close, ArrowLeft, Upload, Check } from '@element-plus/icons-vue'

const userDetailForm = reactive({})
const currentState = ref('normal')

async function getUserDetail() {
  const userRef = await getUserDetailService()
  currentState.value = "normal"
  Object.assign(userDetailForm, userRef.data)
}
getUserDetail()

function goBack() {
  router.go(-1)
}

function changeState(state) {
  currentState.value = state
}

function isDisabled() {
  return currentState.value === "normal"
}

async function editUser() {
  await editUserService(userDetailForm)
  getUserDetail()
}

const uploadUrl = ref("/dev-api/file/upload")

const headers = ref({
  Authorization: "Bearer " + getToken(),
})

async function handleUploadSuccess(res) {
  if (res.code !== 1000) {
    ElMessage.error(res.msg)
  } else {
    const userUpdateDTO = reactive({
      headImage : res.data.name
    })
    await updateHeadImageService(userUpdateDTO)
    getUserDetail()
    ElMessage.success("头像上传成功")
  }
}

</script>

<style lang="scss" scoped>
.user-info-page {
  background: linear-gradient(135deg, #f5f7fa 0%, #e4efe9 100%);
  min-height: 100vh;
  padding: 20px;

  .oj-user-info {
    margin: 0 auto;
    max-width: 1200px;
    width: 100%;

    &.oj-user-info-page {
      .oj-user-info-body {
        background: #fff;
        border-radius: 16px;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
        overflow: hidden;

        .oj-user-info-subtitle-box {
          padding: 30px 40px 0;
          background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
          border-bottom: 1px solid #e9ecef;

          .oj-user-info-subtitle {
            display: flex;
            align-items: center;
            justify-content: space-between;
            height: 40px;

            .oj-user-info-dot {
              font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
              font-weight: 600;
              font-size: 20px;
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

            .opt-box {
              display: flex;
              align-items: center;
              gap: 12px;

              .user-info-edit,
              .user-info-back {
                border-radius: 8px;
                padding: 8px 16px;
                font-weight: 500;
                
                .el-icon {
                  margin-right: 6px;
                }
              }

              .user-info-back {
                color: #666;
                border-color: #dcdfe6;
                
                &:hover {
                  color: #667eea;
                  border-color: #667eea;
                }
              }
            }
          }
        }

        .ku-user-section {
          padding: 40px;
          background: #fff;

          .photo-image-box {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            width: 100%;
            padding-bottom: 30px;
            border-bottom: 1px solid #f0f0f0;
            margin-bottom: 30px;

            .photo-image {
              display: flex;
              align-items: center;
              flex: 1;

              .left {
                img {
                  border-radius: 50%;
                  border: 4px solid #fff;
                  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                  
                  &.user-avatar {
                    object-fit: cover;
                  }
                }
              }

              .right {
                flex: 1;
                margin-left: 24px;
                display: flex;
                flex-direction: column;
                justify-content: center;

                .bold {
                  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
                  font-weight: 600;
                  font-size: 18px;
                  color: #333;
                  margin-bottom: 8px;
                }

                .normal {
                  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
                  font-weight: 400;
                  font-size: 14px;
                  color: #666;
                }
              }
            }

            .upload-btn-container {
              .el-button {
                border-radius: 8px;
                padding: 10px 20px;
                
                .el-icon {
                  margin-right: 6px;
                }
              }
            }
          }

          .form-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 24px 40px;
            margin-bottom: 24px;

            @media (max-width: 768px) {
              grid-template-columns: 1fr;
            }
          }

          :deep(.el-form-item) {
            margin-bottom: 24px;

            .el-form-item__label {
              font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
              font-weight: 600;
              font-size: 14px;
              color: #333;
              margin-bottom: 8px;
              padding: 0;
            }

            .custom-input {
              :deep(.el-input__wrapper) {
                border-radius: 8px;
                border: 1px solid #e0e0e0;
                transition: all 0.3s ease;
                padding: 0 12px;
                height: 44px;
                
                &:hover {
                  border-color: #667eea;
                }
                
                &.is-focus {
                  border-color: #667eea;
                  box-shadow: 0 0 0 1px #667eea;
                }
              }
            }

            .custom-textarea {
              :deep(.el-textarea__inner) {
                border-radius: 8px;
                border: 1px solid #e0e0e0;
                transition: all 0.3s ease;
                padding: 12px;
                resize: vertical;
                
                &:hover {
                  border-color: #667eea;
                }
                
                &:focus {
                  border-color: #667eea;
                  box-shadow: 0 0 0 1px #667eea;
                }
              }
            }

            .gender-radio {
              :deep(.el-radio) {
                margin-right: 24px;
                
                .el-radio__label {
                  font-size: 14px;
                }
              }
            }
          }
        }

        .ku-login-footer {
          padding: 0 40px 40px;
          display: flex;
          justify-content: flex-end;
          gap: 16px;

          .ku-login-footer-btn {
            border-radius: 8px;
            padding: 10px 24px;
            font-weight: 500;
            
            .el-icon {
              margin-right: 6px;
            }

            &.blue {
              background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
              border: none;
              
              &:hover {
                transform: translateY(-1px);
                box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
              }
            }
          }
        }
      }
    }
  }
}

// 查看模式下的样式
.right-info {
  display: inline-block;
  width: 100%;
  color: #333;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-weight: 400;
  font-size: 14px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  min-height: 44px;
  line-height: 1.4;
  display: flex;
  align-items: center;

  &.gray {
    color: #999;
  }

  &.intro {
    white-space: pre-wrap;
    line-height: 1.6;
    align-items: flex-start;
    padding: 12px 0;
    min-height: 60px;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .user-info-page {
    padding: 16px;
    
    .oj-user-info.oj-user-info-page {
      .oj-user-info-body {
        .oj-user-info-subtitle-box {
          padding: 24px 24px 0;
          
          .oj-user-info-subtitle {
            flex-direction: column;
            align-items: stretch;
            gap: 16px;
            height: auto;
            
            .opt-box {
              justify-content: flex-end;
            }
          }
        }
        
        .ku-user-section {
          padding: 24px;
          
          .photo-image-box {
            flex-direction: column;
            align-items: center;
            text-align: center;
            gap: 20px;
            
            .photo-image {
              flex-direction: column;
              text-align: center;
              
              .right {
                margin-left: 0;
                margin-top: 16px;
              }
            }
          }
        }
        
        .ku-login-footer {
          padding: 0 24px 24px;
          justify-content: center;
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .user-info-page .oj-user-info.oj-user-info-page .oj-user-info-body {
    .ku-user-section {
      padding: 20px 16px;
    }
    
    .ku-login-footer {
      flex-direction: column;
      
      .ku-login-footer-btn {
        width: 100%;
      }
    }
  }
}
</style>

<style lang="scss">
.oj-user-info-page {
  .el-form-item__label {
    color: #333 !important;
    font-weight: 600;
  }
}
</style>