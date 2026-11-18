<template>
  <div class="login-page">
    <div class="orange"> </div>
    <div class="blue"></div>
    <div class="blue small"></div>
    <div class="login-box">
      <div class="logo-box">
        <img src="@/assets/刷题logo.jpg">
        <div>
          <div class="sys-name">Online-Judge</div>
          <div class="sys-sub-name">成神之路</div>
        </div>
      </div>
      <div class="form-box-title">
        <span>验证码登录</span>
      </div>
      <div class="form-box">
        <div class="form-item">
          <img src="@/assets/images/shouji.png">
          <el-input v-model="mobileForm.email" type="text" placeholder="请输入邮箱" />
        </div>
        <div class="form-item">
          <img src="@/assets/images/yanzhengma.png">
          <el-input style="width:134px" v-model="mobileForm.code" type="text" placeholder="请输入验证码" />
          <div class="code-btn-box" @click="getCode">
            <span>{{ txt }}</span>
          </div>
        </div>
        <div class="submit-box" @click="loginFun">
          登录/注册
        </div>
      </div>
      <div class="gray-bot">
        <p>注册或点击登录代表您同意 <span>服务条款</span> 和 <span>隐私协议</span></p>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { setToken } from '@/utils/cookie'
import { sendCodeService, codeLoginService } from '@/apis/user'
import router from '@/router'

// 验证码登录表单
let mobileForm = reactive({
  email: '',
  code: ''
})
let txt = ref('获取验证码')
let timer = null
async function getCode() {
  await sendCodeService(mobileForm)
  txt.value = '59s'
  let num = 59
  timer = setInterval(() => {
    num--
    if (num < 1) {
      txt.value = '重新获取验证码'
      clearInterval(timer)
    } else {
      txt.value = num + 's'
    }
  }, 1000)
}

async function loginFun() {
  const loginRef = await codeLoginService(mobileForm)
  setToken(loginRef.data)
  router.push('/c-oj/home/question')

}
</script>
<style lang="scss" scoped>
.login-page {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
  animation: gradientShift 10s ease infinite;
  background-size: 400% 400%;

  .login-box {
    width: 480px;
    height: auto;
    background: rgba(255, 255, 255, 0.95);
    box-shadow: 
      0 25px 50px rgba(0, 0, 0, 0.15), 
      0 0 0 1px rgba(255, 255, 255, 0.3),
      0 0 60px rgba(102, 126, 234, 0.1);
    border-radius: 20px;
    opacity: 1;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    z-index: 2;
    padding: 50px 60px 40px;
    backdrop-filter: blur(20px);
    border: 1px solid rgba(255, 255, 255, 0.4);

    .logo-box {
      display: flex;
      align-items: center;
      margin-bottom: 40px;
      text-align: center;
      justify-content: center;
      flex-direction: column;
      gap: 16px;

      img {
        width: 80px;
        height: 80px;
        border-radius: 50%;
        box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
      }

      .sys-name {
        height: 33px;
        font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
        font-weight: 700;
        font-size: 28px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        line-height: 33px;
        margin-bottom: 8px;
        letter-spacing: 1px;
        text-shadow: 0 2px 4px rgba(0,0,0,0.1);
      }

      .sys-sub-name {
        height: 22px;
        font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
        font-weight: 400;
        font-size: 16px;
        color: rgba(102, 102, 102, 0.8);
        line-height: 22px;
        letter-spacing: 0.5px;
      }
    }

    .form-box-title {
      height: auto;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 30px;

      span {
        font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
        font-weight: 600;
        font-size: 22px;
        color: #333;
        line-height: 33px;
        display: block;
        height: 33px;
        position: relative;
        letter-spacing: 1px;
        cursor: pointer;
        padding-bottom: 8px;

        &.active {
          font-weight: bold;

          &::before {
            position: absolute;
            content: '';
            bottom: 0;
            left: 50%;
            transform: translateX(-50%);
            width: 60%;
            height: 3px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            border-radius: 10px;
          }
        }
      }
    }

    .gray-bot {
      text-align: center;
      margin-top: 30px;
      width: 100%;
      height: auto;
      background: transparent;
      font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
      font-weight: 400;
      font-size: 13px;
      color: rgba(102, 102, 102, 0.8);
      line-height: 1.5;
      padding: 15px 0;
      border-top: 1px solid rgba(0, 0, 0, 0.05);

      p {
        margin: 0;
      }

      span {
        color: #667eea;
        cursor: pointer;
        transition: color 0.3s ease;
        font-weight: 500;

        &:hover {
          color: #764ba2;
          text-decoration: underline;
        }
      }
    }

    :deep(.form-box) {
      .submit-box {
        margin-top: 40px;
        width: 100%;
        height: 52px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-radius: 12px;
        cursor: pointer;
        display: flex;
        justify-content: center;
        align-items: center;
        font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
        font-weight: 600;
        font-size: 16px;
        color: #FFFFFF;
        letter-spacing: 2px;
        transition: all 0.3s ease;
        box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
        position: relative;
        overflow: hidden;

        &::before {
          content: '';
          position: absolute;
          top: 0;
          left: -100%;
          width: 100%;
          height: 100%;
          background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
          transition: left 0.5s;
        }

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 12px 30px rgba(102, 126, 234, 0.5);

          &::before {
            left: 100%;
          }
        }

        &:active {
          transform: translateY(0);
          box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        }

        &.refister-submit {
          margin-top: 72px;
        }
      }

      .form-item {
        display: flex;
        align-items: center;
        width: 100%;
        height: 56px;
        background: rgba(248, 248, 248, 0.8);
        border-radius: 14px;
        margin-bottom: 24px;
        position: relative;
        transition: all 0.3s ease;
        border: 2px solid transparent;
        backdrop-filter: blur(10px);

        &:hover {
          background: rgba(255, 255, 255, 0.9);
          border-color: rgba(102, 126, 234, 0.3);
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1);
        }

        &:focus-within {
          border-color: #667eea;
          background: rgba(255, 255, 255, 0.95);
          box-shadow: 0 6px 20px rgba(102, 126, 234, 0.15);
          transform: translateY(-2px);
        }

        .code-btn-box {
          position: absolute;
          right: 0;
          width: 140px;
          height: 100%;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border-radius: 12px;
          top: 0;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transition: all 0.3s ease;
          font-weight: 500;

          &:hover {
            transform: scale(1.02);
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
          }

          &:active {
            transform: scale(0.98);
          }

          span {
            font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
            font-weight: 500;
            font-size: 14px;
            color: #FFFFFF;
          }
        }

        .error-tip {
          position: absolute;
          width: 140px;
          text-align: right;
          padding-right: 12px;
          height: 20px;
          font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
          font-weight: 400;
          font-size: 14px;
          color: #FD4C40;
          line-height: 20px;
          right: 0;

          &.bottom {
            right: 157px;
          }
        }

        .el-input {
          width: 100%;
          font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
          font-weight: 400;
          font-size: 15px;
          color: #222222;
        }

        .el-input__wrapper {
          border: none;
          box-shadow: none;
          background: transparent;
          width: 100%;
          padding-left: 0;
          height: 100%;
        }

        img {
          width: 22px;
          height: 22px;
          margin: 0 18px;
          opacity: 0.7;
          transition: opacity 0.3s ease;
        }

        &:hover img {
          opacity: 1;
        }
      }
    }
  }

  .orange {
    background: linear-gradient(135deg, #F093FB 0%, #F5576C 100%);
    width: 500px;
    height: 500px;
    border-radius: 50%;
    opacity: 0.3;
    filter: blur(80px);
    left: 5%;
    top: 15%;
    position: absolute;
    animation: float 8s ease-in-out infinite;
  }

  .blue {
    width: 450px;
    height: 450px;
    background: linear-gradient(135deg, #4FACFE 0%, #00F2FE 100%);
    opacity: 0.3;
    filter: blur(80px);
    position: absolute;
    top: 5%;
    left: 65%;
    animation: float 10s ease-in-out infinite;

    &.small {
      width: 300px;
      height: 300px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      top: 55%;
      left: 10%;
      animation: float 9s ease-in-out infinite;
      animation-delay: 1s;
    }
  }

  &::after {
    position: absolute;
    top: 0;
    left: 0;
    height: 100vh;
    bottom: 0;
    right: 0;
    background: linear-gradient(135deg, rgba(255,255,255,0.3) 0%, rgba(248,248,248,0.2) 100%);
    z-index: 1;
    content: '';
    backdrop-filter: blur(5px);
  }

  @keyframes float {
    0%, 100% {
      transform: translateY(0) rotate(0deg) scale(1);
    }
    33% {
      transform: translateY(-30px) rotate(3deg) scale(1.05);
    }
    66% {
      transform: translateY(15px) rotate(-2deg) scale(0.95);
    }
  }

  @keyframes gradientShift {
    0% {
      background-position: 0% 50%;
    }
    50% {
      background-position: 100% 50%;
    }
    100% {
      background-position: 0% 50%;
    }
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-page .login-box {
    width: 90%;
    max-width: 400px;
    padding: 40px 30px 30px;
    margin: 0 20px;
  }

  .login-page .orange,
  .login-page .blue {
    width: 300px;
    height: 300px;
  }

  .login-page .blue.small {
    width: 200px;
    height: 200px;
  }

  :deep(.form-box) .form-item {
    height: 50px !important;
  }

  :deep(.form-box) .submit-box {
    height: 48px !important;
    font-size: 15px !important;
  }
}
</style>