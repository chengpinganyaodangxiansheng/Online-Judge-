<template>
<el-container class="layout-container">

<el-header class="el-header">
<el-dropdown placement="bottom-end">
<span class="el-dropdown__box">
<div>
<strong>当前用户：</strong>超级管理员
</div>
<el-icon>
<ArrowDownBold />
</el-icon>
</span>
<template #dropdown>
<el-dropdown-menu>
<el-dropdown-item @click="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
</el-dropdown-menu>
</template>
</el-dropdown>
</el-header>
<el-main class="layout-bottom-box">
<div class="left">
<el-aside width="200px" class="el-aside">
<el-menu class="el-menu" router>
<el-menu-item index="/oj/layout/cuser">
<el-icon>
<Management />
</el-icon>
<span>用户管理</span>
</el-menu-item>
<el-menu-item index="/oj/layout/question">
<el-icon>
<Management />
</el-icon>
<span>题⽬管理</span>
</el-menu-item>
<el-menu-item index="/oj/layout/exam">
<el-icon>
<Management />
</el-icon>
<span>竞赛管理</span>
</el-menu-item>
</el-menu>
</el-aside>
</div>
<div class="right">
    <RouterView />
</div>
</el-main>
</el-container>
</template>
<script setup>
import {
  Management,
  ArrowDownBold,
  Lock,
  SwitchButton
} from '@element-plus/icons-vue'
import { reactive } from 'vue'
import router from '@/router'
import { getUserInfoService,logoutService } from '@/apis/suser'
import { removeToken } from '@/utils/cookie'

const loginUser = reactive({
  nickName: ''
})

async function getUserInfo() {
  const userInfo = await getUserInfoService()
  loginUser.nickName = userInfo.data.nickName
}
getUserInfo()

async function logout() {
  await ElMessageBox.confirm(
    '确认退出',  
    '温馨提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '退出',
      type: 'warning',
    }
  )
 
  await logoutService()
  removeToken()
  router.push("/oj/login")
}



</script>
<style lang="scss" scoped>
.layout-container {
height: 100vh;
background: linear-gradient(135deg, #f5f7fa 0%, #e4efe9 100%);
overflow: hidden;

.layout-bottom-box {
  display: flex;
  justify-content: space-between;
  height: calc(100vh - 70px);
  overflow: hidden;
  padding: 0;
  margin: 0;
  
  .left {
    margin-right: 20px;
    background: #fff;
    display: flex;
    border-radius: 12px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
    margin-left: 20px;
    margin-top: 15px;
    margin-bottom: 15px;
    overflow: hidden;
    
    :deep(.el-menu) {
      flex: 1;
      border: none;
      background: transparent;
      padding: 10px 0;
      
      .el-menu-item {
        height: 56px;
        line-height: 56px;
        margin: 4px 12px;
        border-radius: 10px;
        font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
        font-weight: 500;
        font-size: 14px;
        color: #666;
        transition: all 0.3s ease;
        position: relative;
        overflow: hidden;
        
        &:before {
          content: '';
          position: absolute;
          left: 0;
          top: 50%;
          transform: translateY(-50%);
          width: 4px;
          height: 0;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          border-radius: 0 4px 4px 0;
          transition: height 0.3s ease;
        }
        
        .el-icon {
          font-size: 18px;
          color: #999;
          transition: all 0.3s ease;
          margin-right: 10px;
        }
        
        span {
          transition: all 0.3s ease;
        }
        
        &:hover {
          background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
          color: #667eea;
          transform: translateX(4px);
          
          .el-icon {
            color: #667eea;
            transform: scale(1.1);
          }
          
          &:before {
            height: 24px;
          }
        }
        
        &.is-active {
          background: linear-gradient(135deg, rgba(102, 126, 234, 0.15) 0%, rgba(118, 75, 162, 0.15) 100%);
          color: #667eea;
          font-weight: 600;
          box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
          
          .el-icon {
            color: #667eea;
          }
          
          &:before {
            height: 32px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          }
        }
      }
    }
  }
  
  .right {
    flex: 1;
    overflow-y: auto;
    background: #fff;
    padding: 25px;
    margin: 15px 20px 15px 0;
    border-radius: 12px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  }
}

.el-aside {
  background-color: transparent;
  
  &__logo {
    height: 120px;
    // background: url('@/assets/logo.png') no-repeat center / 120px auto;
  }
  
  .el-menu {
    border-right: none;
  }
}

.el-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 70px;
  padding: 0 30px;
  box-shadow: 0 2px 15px rgba(102, 126, 234, 0.3);
  position: relative;
  z-index: 10;
  
  .el-dropdown__box {
    display: flex;
    align-items: center;
    padding: 8px 16px;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 25px;
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255, 255, 255, 0.2);
    cursor: pointer;
    transition: all 0.3s ease;
    
    &:hover {
      background: rgba(255, 255, 255, 0.25);
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }
    
    div {
      font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
      font-size: 14px;
      color: white;
      font-weight: 500;
      
      strong {
        font-weight: 600;
        opacity: 0.9;
      }
    }
    
    .el-icon {
      color: white;
      margin-left: 12px;
      font-size: 14px;
      transition: transform 0.3s ease;
    }
    
    &:hover .el-icon {
      transform: translateY(2px);
    }
    
    &:active,
    &:focus {
      outline: none;
    }
  }
}

.el-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #666;
}
}

/* 自定义滚动条 */
.right::-webkit-scrollbar {
  width: 6px;
}

.right::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.right::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
}

.right::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .layout-container .layout-bottom-box {
    flex-direction: column;
    height: auto;
    
    .left {
      margin: 10px;
      margin-bottom: 0;
      width: calc(100% - 20px);
      
      :deep(.el-menu) {
        display: flex;
        flex-direction: row;
        overflow-x: auto;
        
        .el-menu-item {
          flex-shrink: 0;
          min-width: 120px;
        }
      }
    }
    
    .right {
      margin: 10px;
      width: calc(100% - 20px);
    }
  }
  
  .el-header {
    padding: 0 15px;
    height: 60px;
  }
}
</style>