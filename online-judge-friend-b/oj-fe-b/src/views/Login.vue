<template>
<div class="login-page">
    <div class="orange"> </div>
    <div class="blue"></div>
    <div class="blue small"></div>
    <div class="login-box">
      <div class="logo-box">
        <div class="right">
          <div class="sys-name">算法擂台管理员登录</div>
          <div class="sys-sub-name">成为Java高手必经之路</div>
        </div>
      </div>
<div class="form-box">
<div class="form-item">
<img src="../assets/images/shouji.png">
<el-input v-model="userAccount" placeholder="请输⼊账号" />
</div>
<div class="form-item">
<img src="../assets/images/yanzhengma.png">
<el-input v-model="password" type="password" show-password placeholder="请输⼊密码" />
</div>
<div class="submit-box" @click="loginFun">
登录
</div>
</div>
</div>
</div>
</template>
<script setup>
import { ref } from 'vue'
import {loginService} from '@/apis/suser'
import { setToken } from '@/utils/cookie'
import router from '@/router'

const userAccount = ref('')
const password = ref('')


async function loginFun() {
     try {
    const loginResult = await loginService(userAccount.value, password.value)
    console.log("loginResult:", loginResult)
    router.push("/oj/layout")
    setToken(loginResult.data)
  } catch (error) {
    console.log("error:", error)
  }
    
}

</script>
<style lang="scss" scoped>
.login-page {
width: 100vw;
height: 100vh;
position: relative;
overflow: hidden;
background: linear-gradient(135deg, #f5f7fa 0%, #e4efe9 100%);

.login-box {
  width: 480px;
  height: auto;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1), 0 0 0 1px rgba(255, 255, 255, 0.8);
  border-radius: 16px;
  opacity: 1;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 2;
  padding: 50px 60px 40px;
  backdrop-filter: blur(10px);
  
  .logo-box {
    display: flex;
    align-items: center;
    margin-bottom: 40px;
    text-align: center;
    justify-content: center;
    
    img {
      width: 68px;
      height: 68px;
      margin-right: 16px;
    }
    
    .sys-name {
      height: 33px;
      font-family: PingFangSC, PingFang SC;
      font-weight: 700;
      font-size: 26px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      line-height: 33px;
      margin-bottom: 8px;
      letter-spacing: 1px;
    }
    
    .sys-sub-name {
      height: 22px;
      font-family: PingFangSC, PingFang SC;
      font-weight: 400;
      font-size: 15px;
      color: #666;
      line-height: 22px;
      letter-spacing: 0.5px;
    }
  }

  :deep(.form-box) {
    .submit-box {
      margin-top: 60px;
      width: 100%;
      height: 52px;
      background: linear-gradient(135deg, #32C5FF 0%, #667eea 100%);
      border-radius: 12px;
      cursor: pointer;
      display: flex;
      justify-content: center;
      align-items: center;
      font-family: PingFangSC, PingFang SC;
      font-weight: 600;
      font-size: 17px;
      color: #FFFFFF;
      letter-spacing: 2px;
      transition: all 0.3s ease;
      box-shadow: 0 4px 15px rgba(50, 197, 255, 0.3);
      
      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 25px rgba(50, 197, 255, 0.4);
      }
      
      &:active {
        transform: translateY(0);
      }
    }
    
    .form-item {
      display: flex;
      align-items: center;
      width: 100%;
      height: 52px;
      background: #F8F8F8;
      border-radius: 12px;
      margin-bottom: 24px;
      position: relative;
      transition: all 0.3s ease;
      border: 2px solid transparent;
      
      &:focus-within {
        border-color: #32C5FF;
        background: #fff;
        box-shadow: 0 4px 12px rgba(50, 197, 255, 0.15);
      }
      
      .code-btn-box {
        position: absolute;
        right: 0;
        width: 151px;
        height: 48px;
        background: #32C5FF;
        border-radius: 8px;
        top: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        
        span {
          font-family: PingFangSC, PingFang SC;
          font-weight: 400;
          font-size: 16px;
          color: #FFFFFF;
        }
      }
      
      .error-tip {
        position: absolute;
        width: 140px;
        text-align: right;
        padding-right: 12px;
        height: 20px;
        font-family: PingFangSC, PingFang SC;
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
        font-family: PingFangSC, PingFang SC;
        font-weight: 400;
        font-size: 16px;
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
      }
    }
  }
}

.orange {
  background: #F0714A;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  opacity: 0.4;
  filter: blur(60px);
  left: 10%;
  top: 20%;
  position: absolute;
  animation: float 6s ease-in-out infinite;
}

.blue {
  width: 350px;
  height: 350px;
  background: #32C5FF;
  opacity: 0.4;
  filter: blur(60px);
  position: absolute;
  top: 10%;
  left: 70%;
  animation: float 8s ease-in-out infinite;
  
  &.small {
    width: 200px;
    height: 200px;
    top: 60%;
    left: 15%;
    animation: float 7s ease-in-out infinite;
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
  background: linear-gradient(135deg, rgba(255,255,255,0.9) 0%, rgba(248,248,248,0.8) 100%);
  z-index: 1;
  content: '';
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(5deg);
  }
}
}
</style>