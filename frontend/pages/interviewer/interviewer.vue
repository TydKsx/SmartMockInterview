<template>
  <div class="ai-container">
    <!-- 主体内容 -->
    <div class="main-content" >
      <!-- 左侧AI列表 -->
      <div class="left-panel">
        <div class="panel-header">
          <span>我的AI助手</span>
          <el-button
            type="primary"
            icon="el-icon-plus"
            size="mini"
            style="border-radius: 30px;"
            @click="showAddDialog"
          ></el-button>
        </div>
        <div class="ai-list">
          <el-scrollbar class="vertical-only-scrollbar" style="height:100%">
            <el-menu
              :default-active="activeAi"
              @select="handleSelect"
            >
            <el-menu-item
              v-for="item in aiList"
              :key="item.interviewerId"
              :index="item.interviewerId"
              style="display: flex; justify-content: space-between; align-items: center"
            >
              <span>{{ item.name.length > 10 ? item.name.substring(0, 10) + '...' : item.name }}</span>
              <el-popover
                placement="right"
                width="110"
                trigger="hover"
                :popper-class="'ai-popover'"
              >
                <div class="action-menu">
                  <div class="action-item" @click.stop="renameAi(item.interviewerId)">
                    <i class="el-icon-edit"></i>
                    <span>重命名</span>
                  </div>
                  <div class="action-item delete" @click.stop="deleteAi(item.interviewerId)">
                    <i class="el-icon-delete"></i>
                    <span>删除</span>
                  </div>
                </div>
                <el-button slot="reference" type="text" icon="el-icon-more" class="more-btn"></el-button>
              </el-popover>
            </el-menu-item>
            </el-menu>
          </el-scrollbar>
        </div>
      </div>

      <!-- 分割线 -->
      <div class="divider"></div>

      <!-- 右侧AI设置详情 -->
      <div class="right-panel no-vertical-scroll">
        <div v-if="activeAi" class="detail-container ">
          <div class="detail-header">
            <h2>{{ currentAi.name || '未命名AI' }}</h2>
          </div>

          <el-form :model="currentAi" label-width="120px" class="ai-form">
            <!-- 知识库选择 -->
            <el-form-item label="知识库">
              <el-select
                v-model="currentAi.knowledgeBaseId"
                placeholder="请选择知识库"
                style="width: 100%"
              >
                <el-option
                  v-for="db in databases"
                  :key="db.knowledgeBaseId"
                  :label="db.databaseName"
                  :value="db.knowledgeBaseId"
                ></el-option>
              </el-select>
            </el-form-item>

            <!-- 提示词设置 -->
            <el-form-item label="提示词模板">
              <el-input
                type="textarea"
                :rows="10"
                v-model="currentAi.customPrompt"
                placeholder="请输入自定义提示词"
              ></el-input>
            </el-form-item>

            <!-- AI设置项 -->
            <!-- 修改设置项的绑定方式 -->
            <el-form-item
              v-for="setting in currentAi.settingsList"
              :key="setting.id"
              :label="setting.settingName"
            >
              <div class="setting-item">
                <el-tooltip
                  :content="setting.description"
                  placement="top"
                  style="height: 40px;"
                  >
                  <i class="el-icon-info"></i>
                </el-tooltip>

                <el-slider
                  v-model="setting.extent"
                  :min="1"
                  :max="10"
                  show-input
                  style="width: 80%"
                ></el-slider>
              </div>
            </el-form-item>


            <el-form-item>
              <el-button
                type="primary"
                @click="saveAi"
                style="margin-left:200px ;"
              >
                保存设置
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <div v-else class="empty-state">
          <el-empty description="请从左侧选择一个AI或创建新的AI"></el-empty>
        </div>
      </div>
    </div>

    <!-- 添加AI对话框 -->
    <el-dialog title="新建AI助手" :visible.sync="addDialogVisible" width="30%">
      <el-form :model="newAi" label-width="80px">
        <el-form-item label="名称" required>
          <el-input
            v-model="newAi.name"
            placeholder="请输入AI名称"
          ></el-input>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="addDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="addAi">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import axios from "../../store/utils/interceptor"
export default {
  data() {
    return {
      loading: true,
      activeAi: null,
      aiList: [],
      currentAi: {
        interviewerId: '',
        name: '',
        userId: null,
        knowledgeBaseId: '',
        customPrompt: '',
        settingsList: [],
      },
      newAi: {
        interviewerId: '',
        name: '',
        userId: null,
        knowledgeBaseId: '',
        customPrompt: '',
        settingsList: [],
      },
      databases: [],
      allSettings: [],
      addDialogVisible: false
    }
  },
  //async mounted() {
  //  await this.fetchAiList();
  //  await this.fetchDatabases();
  //  await this.fetchAiSettings();
  //},
  async mounted() {
    try {
      await this.fetchAiSettings();
      await Promise.all([
        this.fetchDatabases(),
        this.fetchAiList(),
      ]);
      if (this.aiList.length > 0) {
        this.handleSelect(this.aiList[0].interviewerId);
      }
    } catch (error) {
      this.$message.error("初始化失败：" + error.message);
    }
  },
  methods: {
    async fetchAiList() {
      try {
        const res = await axios.get('/api/Interviewer/list');
        console.log(res.data)
        this.aiList = (res.data || []).filter(ai => ai.name !== '简历优化助手');
      } catch (error) {
        this.$message.error(error);
        console.error(error);
      }
    },
    async fetchDatabases() {
      try {
        const res = await axios.get('/api/MilvusDatabase');
        this.databases = res.data || [];
      } catch (error) {
        this.$message.error(error);
        console.error(error);
      }
    },
    async fetchAiSettings() {
      try {
        const res = await axios.get('/api/Interviewer/getAiSettings');
        this.allSettings = res.data || [];
        console.log(this.allSettings)

      } catch (error) {
        this.$message.error(error);
        console.error(error);
      }
    },
    //       handleSelect(index) {
    //   this.activeAi = index;
    //   const selectedAi = this.aiList.find(ai => ai.interviewerId === index);
    //   if (selectedAi) {
    //     this.currentAi = JSON.parse(JSON.stringify(selectedAi));

    //     // 创建现有设置的映射表（以id为key）
    //     const existingSettingsMap = new Map();
    //     if (this.currentAi.settingsList) {
    //       this.currentAi.settingsList.forEach(item => {
    //         existingSettingsMap.set(item.id, item.extent); // 只保存extent值
    //       });
    //     }

    //     // 重新构建settingsList：合并allSettings的属性和保存的extent值
    //     this.currentAi.settingsList = this.allSettings.map(template => {
    //       return {
    //         ...template,                // 展开模板属性（id, settingName, description）
    //         extent: existingSettingsMap.has(template.id)
    //                ? existingSettingsMap.get(template.id)
    //                : 5                  // 默认值
    //       };
    //     });
    //   }
    // },
    handleSelect(index) {
      this.activeAi = index;
      const selectedAi = this.aiList.find(ai => ai.interviewerId === index);

      if (selectedAi) {
        // 1. 深拷贝选中的AI数据
        this.currentAi = JSON.parse(JSON.stringify(selectedAi));

        // 2. 如果 settingsList 不存在，初始化为空数组
        if (!this.currentAi.settingsList) {
          this.currentAi.settingsList = [];
        }

        // 3. 构建一个 Map，存储已有的 extent 值（如果有）
        const existingSettingsMap = new Map();
        this.currentAi.settingsList.forEach(item => {
          existingSettingsMap.set(item.id, item.extent);
        });

        // 4. 重新构建 settingsList，确保所有设置项都有默认值
        this.currentAi.settingsList = this.allSettings.map(template => ({
          ...template,  // 继承模板属性（id, settingName, description）
          extent: existingSettingsMap.has(template.id)
            ? existingSettingsMap.get(template.id)
            : 5,   // 默认值
        }));

        // 5. 强制更新视图（确保UI刷新）
        this.$nextTick(() => this.$forceUpdate());
      }
    },
    // showAddDialog() {
    //   this.newAi = {
    //     name: '',
    //     knowledgeBaseId: this.databases[0]?.knowledgeBaseId || '',
    //     customPrompt: '',
    //     settingsList: this.allSettings.map(template => ({
    //       ...template,    // 继承模板属性
    //       extent: 5       // 设置默认值
    //     }))
    //   };
    //   this.addDialogVisible = true;
    // },
    showAddDialog() {
      this.newAi = {
        name: '',
        knowledgeBaseId: this.databases[0]?.knowledgeBaseId || '',
        customPrompt: '',
        settingsList: this.allSettings.map(template => ({
          ...template,    // 继承模板属性
          extent: 5,      // 默认值
        })),
      };
      this.addDialogVisible = true;
    },
    async addAi() {
      if (!this.newAi.name) {
        this.$message.warning('请输入AI名称');
        return;
      }

      try {
        await axios.post('/api/Interviewer/saveOrUpdate', this.newAi);
        this.$message.success('添加成功');
        this.addDialogVisible = false;
        await this.fetchAiList();
      } catch (error) {
        this.$message.error('添加失败');
        console.error(error);
      }
    },
    async saveAi() {
      try {
        await axios.post('/api/Interviewer/saveOrUpdate', this.currentAi);
        this.$message.success('保存成功');
        await this.fetchAiList();
      } catch (error) {
        this.$message.error('保存失败');
        console.error(error);
      }
    },
    deleteAi(id) {
      this.$confirm('确定要删除这个AI吗?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          await axios.delete(`/api/Interviewer/delete/${id}`);
          this.$message.success('删除成功');
          await this.fetchAiList();
          if (this.activeAi === id) {
            this.activeAi = null;
          }
        } catch (error) {
          this.$message.error('删除失败');
          console.error(error);
        }
      }).catch(() => { });
    },
    renameAi(id) {
      // 这里可以添加重命名的逻辑
      this.$prompt('请输入新的AI名称', '重命名', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '名称不能为空'
      }).then(async ({ value }) => {
        try {
          // 找到对应的AI
          const ai = this.aiList.find(item => item.interviewerId === id);
          if (ai) {
            ai.name = value;
            await axios.post('/api/Interviewer/saveOrUpdate', ai);
            this.$message.success('重命名成功');
            await this.fetchAiList();
          }
        } catch (error) {
          this.$message.error('重命名失败');
          console.error(error);
        }
      }).catch(() => { });
    }
  }
}
</script>

<style scoped>
.ai-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.main-content {
  display: flex;
  height: calc(100vh - 120px);
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  margin-left: 40px;
  margin-top: 30px;
  margin-bottom: 30px;
  margin-right: 40px;
  overflow: hidden;
}

.left-panel {
  width: 350px;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebeef5;
}

.panel-header {
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
}

.panel-header span {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.ai-list {
  flex: 1;
  overflow: hidden;
  background: #fff;
}

.el-menu {
  border-right: none;
}

.el-menu-item {
  height: 50px;
  line-height: 50px;
  font-size: 14px;
  color: #606266;
  transition: all 0.3s;
  position: relative;
}

.el-menu-item:hover {
  background-color: #f5f7fa;
  color: #409eff;
}

.el-menu-item.is-active {
  color: #409eff;
  background-color: #ecf5ff;
}

.more-btn {
  color: #909399;
  font-size: 16px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.el-menu-item:hover .more-btn {
  opacity: 1;
}

.divider {
  width: 1px;
  background-color: #ebeef5;
}

.right-panel {
  flex: 1;
  padding: 24px;
  padding-right: 200px;
  overflow-y: auto;
  background: #fff;
}

.detail-container {
  max-width: 800px;
  margin: 0 auto;
}

.detail-header h2 {
  color: #303133;
  font-size: 20px;
  font-weight: 500;
  margin: 0;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.ai-form {
  margin-top: 24px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.setting-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 8px 0;
}

.setting-item .el-icon-info {
  margin-right: 12px;
  color: #909399;
  cursor: pointer;
  font-size: 16px;
}

.el-slider {
  margin-left: 12px;
}

.el-form-item {
  margin-bottom: 20px;
}

.el-form-item__label {
  color: #606266;
  font-weight: 500;
}

.el-button--primary {
  background-color: #409eff;
  border-color: #409eff;
}

.el-button--primary:hover {
  background-color: #66b1ff;
  border-color: #66b1ff;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}

.el-empty__description p {
  color: #909399;
  font-size: 14px;
}

.el-dialog {
  border-radius: 8px;
}

.el-dialog__header {
  padding: 20px;
  border-bottom: 1px solid #ebeef5;
}

.el-dialog__title {
  font-size: 16px;
  color: #303133;
}

.el-dialog__body {
  padding: 20px;
}

.el-dialog__footer {
  padding: 12px 20px;
  border-top: 1px solid #ebeef5;
}

.el-input__inner,
.el-textarea__inner {
  border-radius: 4px;
  border: 1px solid #dcdfe6;
}

.el-input__inner:focus,
.el-textarea__inner:focus {
  border-color: #409eff;
}

.el-select {
  width: 100%;
}

/* 方法1：推荐 - 使用深度选择器隐藏水平滚动条 */
:deep(.vertical-only-scrollbar .el-scrollbar__wrap) {
  overflow-x: hidden !important;
  padding-bottom: 0 !important;
}

.right-panel.no-vertical-scroll {
  overflow-y: hidden !important;
}

/* 操作菜单样式 - 从interviewContainer.vue复制 */
.action-menu {
  padding: 4px 0;
}

.action-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 8px;
  margin: 0 4px;
}

.action-item:hover {
  background-color: #f5f7fa;
}

.action-item.delete:hover {
  color: #f56c6c;
}

.action-item i {
  margin-right: 8px;
  font-size: 14px;
}

/* 自定义弹窗样式 */
.ai-popover {
  padding: 0;
  min-width: 110px;
  border-radius: 22px !important;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
}
</style>