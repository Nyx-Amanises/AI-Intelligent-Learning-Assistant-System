<template>
  <section
    class="learning-page material-library"
    :aria-labelledby="showContentHub && isMobileViewport ? undefined : 'material-library-title'"
    :aria-label="showContentHub && isMobileViewport ? '我的内容' : undefined"
  >
    <header v-show="!showContentHub || !isMobileViewport" class="page-header">
      <div>
        <h1 id="material-library-title" class="page-title">{{ showContentHub ? '我的内容' : '资料库' }}</h1>
        <p class="page-desc">上传讲义、教材或笔记，解析完成后可以阅读原文、生成总结和练习题。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button @click="textDialogVisible = true"><AppIcon name="summary" :size="16" />粘贴文本</el-button>
        <el-button type="primary" @click="uploadDialogVisible = true"
          ><AppIcon name="upload" :size="16" />上传资料</el-button
        >
      </div>
    </header>
    <MobileContentHub v-if="showContentHub" />

    <div v-show="!showContentHub" class="workspace-panel material-library__panel">
      <div class="workspace-toolbar workspace-toolbar--filter">
        <div
          id="material-list-filters"
          class="filter-bar mobile-filter-bar"
          :class="{ 'is-expanded': mobileFiltersOpen }"
          role="search"
          aria-label="筛选资料"
        >
          <el-input
            v-model="searchKeyword"
            clearable
            aria-label="按标题、标签或说明搜索"
            placeholder="搜索标题、标签或说明"
            @keyup.enter="autoQuery.runNow"
          >
            <template #prefix><AppIcon name="search" :size="16" /></template>
          </el-input>
          <MobileFilterToggle v-model="mobileFiltersOpen" :count="mobileFilterCount" controls="material-list-filters" />
          <el-select
            v-model="filters.materialType"
            class="mobile-filter-detail"
            clearable
            aria-label="资料类型"
            placeholder="全部类型"
          >
            <el-option v-for="item in materialTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select
            v-model="filters.parseStatus"
            class="mobile-filter-detail"
            clearable
            aria-label="解析状态"
            placeholder="全部状态"
          >
            <el-option label="待解析" value="PENDING" />
            <el-option label="解析中" value="PROCESSING" />
            <el-option label="已解析" value="SUCCESS" />
            <el-option label="解析失败" value="FAILED" />
          </el-select>
          <el-button v-if="hasActiveFilters" class="mobile-filter-detail" text @click="resetFilters"
            >清除筛选</el-button
          >
        </div>
        <el-button text :loading="tableLoading" aria-label="刷新资料列表" @click="loadMaterials">
          <AppIcon v-if="!tableLoading" name="refresh" :size="16" /><span class="workspace-toolbar__refresh-label"
            >刷新</span
          >
        </el-button>
      </div>

      <div v-if="tableLoading" class="record-list" role="status" aria-label="正在加载资料">
        <div v-for="i in 4" :key="i" class="list-row record-row record-row--loading" aria-hidden="true">
          <span class="skeleton record-row__sk-lead" />
          <div class="record-row__main">
            <span class="skeleton record-row__sk-title" />
            <span class="skeleton record-row__sk-meta" />
          </div>
          <span class="skeleton record-row__sk-actions" />
        </div>
      </div>
      <EmptyState v-else-if="loadError" variant="error" title="资料列表没有加载出来" :description="loadError">
        <el-button type="primary" @click="loadMaterials">重新加载</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!materialRows.length && hasActiveFilters"
        icon="search"
        title="没有找到匹配的资料"
        description="换个关键词，或者清除筛选条件再看看。"
      >
        <el-button @click="resetFilters">清除筛选</el-button>
      </EmptyState>
      <EmptyState
        v-else-if="!materialRows.length"
        icon="book-open"
        title="还没有资料"
        description="上传讲义或教材，也可以直接粘贴一段文字。解析完成后就能生成总结和练习题。"
      >
        <el-button @click="router.push('/resources')"><AppIcon name="search" :size="16" />找学习资料</el-button>
        <el-button type="primary" @click="uploadDialogVisible = true"
          ><AppIcon name="upload" :size="16" />上传资料</el-button
        >
        <el-button @click="textDialogVisible = true">粘贴文本</el-button>
      </EmptyState>
      <ul v-else class="record-list">
        <li v-for="row in materialRows" :key="row.id" class="list-row record-row">
          <button
            type="button"
            class="material-row__cover"
            :aria-label="`打开${row.title}`"
            @click="viewDetail(row.id)"
          >
            <MaterialCover :material-id="row.id" :title="row.title" :cover-url="row.coverUrl" />
          </button>
          <div class="record-row__main">
            <h2 class="record-row__title">
              <button type="button" :title="row.title" @click="viewDetail(row.id)">{{ row.title }}</button>
              <StatusPill v-if="isProcessing(row)" tone="processing" :label="getProcessingText(row)" />
              <StatusPill v-else-if="row.parseStatus === 'FAILED'" tone="failed" label="解析失败" />
              <StatusPill v-else-if="row.parseStatus === 'PENDING'" tone="muted" label="待解析" />
            </h2>
            <p v-if="row.description" class="record-row__desc" :title="row.description">{{ row.description }}</p>
            <p v-if="row.parseStatus === 'FAILED'" class="material-row__error">
              {{ row.parseErrorMessage || '没有记录失败原因，重新解析一次可以看到详情。' }}
            </p>
            <p class="record-row__meta">
              <span class="visually-hidden">{{ formatMaterialType(row.materialType) }}</span>
              <time :datetime="row.createdAt" :title="formatDateTime(row.createdAt)"
                >{{ formatRelativeDay(row.createdAt) }}添加</time
              >
              <span v-if="row.summaryStatus === 'SUCCESS'" class="material-row__summary"
                >有总结<GenerationModeBadge
                  v-if="row.summaryGenerationMode === 'MOCK'"
                  :mode="row.summaryGenerationMode"
              /></span>
              <span v-for="tag in row.tagList.slice(0, 3)" :key="tag" class="record-row__chip" :title="tag">{{
                tag
              }}</span>
              <span v-if="row.tagList.length > 3" class="record-row__chip" :title="row.tagList.slice(3).join('、')"
                >+{{ row.tagList.length - 3 }}</span
              >
            </p>
          </div>
          <div class="record-row__actions" role="group" :aria-label="`${row.title}的操作`">
            <el-button size="small" @click="viewDetail(row.id)">打开</el-button>
            <el-button
              size="small"
              text
              :disabled="row.parseStatus !== 'SUCCESS' || row.summaryStatus === 'RUNNING'"
              @click="goSummary(row.id)"
              >总结</el-button
            >
            <el-button size="small" text :disabled="row.parseStatus !== 'SUCCESS'" @click="goQuiz(row.id)"
              >出题</el-button
            >
            <el-dropdown
              trigger="click"
              :disabled="actionLoadingId === row.id"
              @command="(command: string) => handleMaterialCommand(command, row)"
            >
              <IconMenuButton :label="`${row.title}的更多操作`" :loading="actionLoadingId === row.id" />
              <template #dropdown>
                <el-dropdown-menu class="material-library-menu">
                  <el-dropdown-item command="retrieval" :disabled="row.parseStatus !== 'SUCCESS'"
                    >检索预览</el-dropdown-item
                  >
                  <el-dropdown-item
                    command="embedding"
                    :disabled="row.parseStatus !== 'SUCCESS' || row.embeddingStatus === 'RUNNING'"
                    >{{ getEmbeddingActionLabel(row) }}</el-dropdown-item
                  >
                  <el-dropdown-item command="edit" divided>编辑信息</el-dropdown-item>
                  <el-dropdown-item command="cover">更换封面</el-dropdown-item>
                  <el-dropdown-item command="parse">{{
                    row.parseStatus === 'SUCCESS' ? '重新解析' : '解析资料'
                  }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided class="menu-item--danger">删除资料</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </li>
      </ul>

      <footer v-if="!tableLoading && !loadError && total > 0" class="workspace-pagination">
        <span class="workspace-pagination__meta">{{
          hasActiveFilters ? `找到 ${total} 份资料` : `共 ${total} 份资料`
        }}</span>
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]"
          :pager-count="5"
          layout="sizes, prev, pager, next"
          :total="total"
          @current-change="loadMaterials"
          @size-change="loadMaterials"
        />
      </footer>
    </div>

    <el-dialog
      v-model="uploadDialogVisible"
      class="material-library-modal"
      title="上传资料"
      width="min(560px, calc(100vw - 32px))"
      :close-on-click-modal="!uploading"
      :close-on-press-escape="!uploading"
      :show-close="!uploading"
      :before-close="closeUploadDialog"
      destroy-on-close
    >
      <div class="material-library-upload">
        <el-upload
          drag
          :show-file-list="false"
          :http-request="uploadMaterial"
          :before-upload="beforeUpload"
          :disabled="uploading"
          accept=".pdf,.docx,.txt"
        >
          <span class="material-library-upload__icon"><AppIcon name="upload" :size="24" /></span>
          <strong>{{
            uploading ? '正在上传…' : isMobileViewport ? '选择学习资料' : '把文件拖到这里，或点击选择'
          }}</strong>
          <span>支持 PDF、Word（.docx）和 TXT</span>
          <span v-if="uploading" class="material-library__spinner" role="status" aria-label="上传中"></span>
        </el-upload>
        <p class="material-library-upload__note">扫描版 PDF 里没有可以复制的文字，需要先做 OCR 识别再上传。</p>
      </div>
      <template #footer
        ><div class="material-library-dialog__footer">
          <el-button :disabled="uploading" @click="closeUploadDialog()">{{
            uploading ? '正在上传…' : returnToHome ? '返回首页' : '取消'
          }}</el-button>
        </div></template
      >
    </el-dialog>

    <el-dialog
      v-model="textDialogVisible"
      class="material-library-modal"
      title="粘贴文本"
      width="min(720px, calc(100vw - 32px))"
      top="6vh"
      destroy-on-close
    >
      <el-form label-position="top" class="material-library-form">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="例如：数据库系统原理 第三章事务管理" />
        </el-form-item>
        <div class="material-library-form__grid">
          <el-form-item label="格式">
            <el-select v-model="form.materialType" class="material-library-form__full">
              <el-option label="纯文本" value="TEXT" />
              <el-option label="Markdown" value="MARKDOWN" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="form.tags" placeholder="用逗号或顿号分隔，例如：数据库、期末复习" />
          </el-form-item>
        </div>
        <el-form-item label="难度">
          <el-slider
            v-model="form.difficultyLevel"
            :min="1"
            :max="5"
            show-stops
            :marks="DIFFICULTY_LABELS"
            :format-tooltip="formatDifficulty"
            :format-value-text="formatDifficulty"
            aria-label="资料难度"
          />
        </el-form-item>
        <el-form-item label="说明（选填）">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="例如来源、版本或用途"
          />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="form.contentText"
            type="textarea"
            :rows="8"
            maxlength="10000"
            show-word-limit
            placeholder="粘贴课堂笔记、知识点整理或教材摘录"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="material-library-dialog__footer">
          <el-button @click="textDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="createMaterial">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editDialogVisible"
      class="material-library-modal"
      title="编辑资料信息"
      width="min(620px, calc(100vw - 32px))"
      destroy-on-close
    >
      <el-form label-position="top" class="material-library-form" @submit.prevent="saveMaterialInfo">
        <el-form-item label="标题" required :error="editTitleError">
          <el-input
            v-model="editForm.title"
            maxlength="200"
            show-word-limit
            placeholder="输入标题"
            @input="editTitleError = ''"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" maxlength="255" placeholder="用逗号或顿号分隔，例如：期末复习、第三章" />
        </el-form-item>
        <el-form-item label="难度">
          <el-slider
            v-model="editForm.difficultyLevel"
            :min="1"
            :max="5"
            show-stops
            :marks="DIFFICULTY_LABELS"
            :format-tooltip="formatDifficulty"
            :format-value-text="formatDifficulty"
            aria-label="资料难度"
          />
        </el-form-item>
        <el-form-item label="说明（选填）">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如来源、版本或用途"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="material-library-dialog__footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingInfo" @click="saveMaterialInfo">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-model="drawerVisible"
      class="material-library-modal"
      :class="{ 'material-library-modal--book': documentUrl }"
      title="资料详情"
      :size="documentUrl ? 'min(1100px, 100vw)' : 'min(720px, 100vw)'"
      header-class="material-library-detail__header"
      :show-close="false"
      :aria-modal="!noteVisible"
      :before-close="closeMaterialDetail"
      @focusout.capture="allowNoteFocus"
      @keydown.tab.capture="allowNoteFocus"
      @closed="clearMaterialDeepLink"
    >
      <template #header="{ titleId }">
        <div class="material-library-detail__navigation">
          <button
            type="button"
            class="material-library-detail__back"
            :aria-label="returnToHome ? '返回首页' : returnToResources ? '返回找资料' : '返回资料库'"
            @click="closeMaterialDetail()"
          >
            <MobileIcon name="back" :size="24" />
          </button>
          <h2 :id="titleId" class="material-library-detail__title">资料详情</h2>
          <span aria-hidden="true" />
        </div>
      </template>
      <div
        ref="materialReader"
        class="material-library-detail"
        :class="{ 'material-library-detail--book': documentUrl }"
      >
        <div v-if="detailLoading" class="material-library-detail__loading" role="status">
          <span class="material-library__spinner"></span>
          <p>正在打开资料…</p>
        </div>
        <EmptyState
          v-else-if="detailError"
          variant="error"
          icon="refresh"
          title="这份资料打不开"
          :description="detailError"
        >
          <el-button @click="viewDetail(detailId)">重试</el-button>
        </EmptyState>
        <template v-else-if="detail">
          <div class="material-library-detail__intro">
            <div class="material-library-detail__heading">
              <button
                type="button"
                class="material-detail-cover"
                aria-label="更换封面"
                @click="openCoverEditor(detail)"
              >
                <MaterialCover :material-id="detail.id" :title="detail.title" :cover-url="detail.coverUrl" />
                <span class="material-detail-cover__edit"><AppIcon name="camera" :size="14" /></span>
              </button>
              <div>
                <h2 :title="detail.title">{{ detail.title }}</h2>
                <p class="material-library-detail__brief">
                  {{ documentUrl ? 'PDF' : formatMaterialType(detail.materialType) }}
                </p>
              </div>
            </div>
            <el-alert
              v-if="detail.parseStatus === 'FAILED'"
              :title="detail.parseErrorMessage || '没有记录失败原因，重新解析一次可以看到详情。'"
              type="error"
              :closable="false"
              show-icon
            />
            <div class="material-library-detail__actions material-library-detail__study-actions">
              <button
                v-if="!documentUrl"
                type="button"
                class="material-note-button"
                aria-label="记笔记"
                title="记笔记"
                @mousedown.prevent
                @click="recordMaterialNote()"
              >
                <MobileIcon name="pencil" :size="20" />
              </button>
              <el-button
                v-if="detail.parseStatus !== 'SUCCESS'"
                type="primary"
                :loading="actionLoadingId === detail.id || detail.parseStatus === 'PROCESSING'"
                :disabled="detail.parseStatus === 'PROCESSING'"
                @click="parseMaterial(detail.id)"
                >{{ detail.parseStatus === 'PROCESSING' ? '解析中' : '解析资料' }}</el-button
              >
              <el-button v-else type="primary" aria-label="查看总结" title="查看总结" @click="goSummary(detail.id)"
                ><AppIcon name="summary" :size="20" /><span class="material-library-detail__action-label"
                  >查看总结</span
                ></el-button
              >
              <el-button
                :disabled="detail.parseStatus !== 'SUCCESS'"
                aria-label="出题"
                title="出题"
                @click="goQuiz(detail.id)"
                ><AppIcon name="quiz" :size="20" /><span class="material-library-detail__action-label"
                  >出题</span
                ></el-button
              >
            </div>
          </div>
          <PdfBookReader
            v-if="documentUrl && drawerVisible"
            :key="detail.id"
            class="material-library-detail__book"
            :url="documentUrl"
            :title="detail.title"
            :resource-id="detail.resourceId || undefined"
            :initial-page="readerStartPage"
            prefer-original
            @progress="readingProgress = $event"
            @page-change="readerStartPage = $event"
          />
          <p v-if="documentUrl" class="material-library-detail__progress">已读 {{ readingProgress }}%</p>
          <article v-else-if="readableParagraphs.length" class="material-library-detail__body" aria-label="资料正文">
            <p v-for="(paragraph, index) in readableParagraphs" :key="index">{{ paragraph }}</p>
          </article>
          <div v-else class="material-library-detail__empty">解析后可以在这里阅读原文。</div>
          <details v-if="!documentUrl && !isMobileViewport" :key="detail.id" class="material-library-detail__tools">
            <summary>资料信息与工具<MobileIcon name="down" :size="18" /></summary>
            <dl class="material-library-detail__meta">
              <div>
                <dt>添加时间</dt>
                <dd>{{ formatDateTime(detail.createdAt) }}</dd>
              </div>
              <div>
                <dt>内容长度</dt>
                <dd>{{ formatCharacters(detail.totalCharacters) }}</dd>
              </div>
              <div>
                <dt>解析状态</dt>
                <dd>{{ formatParseStatus(detail.parseStatus) }}</dd>
              </div>
              <div>
                <dt>分块方式</dt>
                <dd>{{ formatChunking(detail) }}</dd>
              </div>
              <div>
                <dt>学习总结</dt>
                <dd>
                  {{ formatSummaryStatus(detail.summaryStatus) }}
                  <GenerationModeBadge v-if="detail.summaryStatus === 'SUCCESS'" :mode="detail.summaryGenerationMode" />
                </dd>
              </div>
              <div>
                <dt>向量索引</dt>
                <dd>{{ formatEmbeddingStatus(detail.embeddingStatus) }} · {{ formatEmbeddingProgress(detail) }}</dd>
              </div>
              <div>
                <dt>标签</dt>
                <dd>{{ getTags(detail.tags).join('、') || '没有标签' }}</dd>
              </div>
              <div>
                <dt>难度</dt>
                <dd>{{ formatDifficulty(detail.difficultyLevel) }}</dd>
              </div>
              <div class="material-library-detail__meta-wide">
                <dt>说明</dt>
                <dd :class="{ 'is-empty': !detail.description }">
                  {{ detail.description || '还没有说明，可以在“编辑信息”里补充来源或版本。' }}
                </dd>
              </div>
            </dl>
            <div class="material-library-detail__actions">
              <el-button @click="openEditDialog(detail)">编辑信息</el-button>
            </div>
            <section class="material-library-detail__retrieval" aria-labelledby="material-detail-retrieval-title">
              <div class="material-library-detail__retrieval-heading">
                <h3 id="material-detail-retrieval-title">向量检索</h3>
                <RouterLink to="/ai-config#vector-model"
                  >配置向量模型<AppIcon name="arrow-up-right" :size="14"
                /></RouterLink>
              </div>
              <p>
                {{
                  detail.parseStatus === 'SUCCESS'
                    ? '生成向量后，可以按问题的意思找到资料里的相关片段。'
                    : '先解析资料，再生成向量。'
                }}
              </p>
              <div class="material-library-detail__actions">
                <el-button
                  :disabled="detail.parseStatus !== 'SUCCESS' || detail.embeddingStatus === 'RUNNING'"
                  :loading="actionLoadingId === detail.id"
                  @click="generateEmbedding(detail)"
                  >{{ getEmbeddingActionLabel(detail) }}</el-button
                >
                <el-button :disabled="detail.parseStatus !== 'SUCCESS'" @click="openRetrievalPreview(detail)"
                  ><AppIcon name="search" :size="16" />检索预览</el-button
                >
                <RouterLink
                  v-if="detail.embeddingStatus === 'RUNNING'"
                  class="material-library-detail__task-link"
                  to="/ai-tasks"
                  >查看进度</RouterLink
                >
              </div>
            </section>
          </details>
        </template>
      </div>
    </el-drawer>

    <MaterialCoverEditor v-model="coverEditorVisible" :material="coverTarget" @updated="updateCover" />

    <el-dialog
      v-model="parseDialogVisible"
      :title="parseTarget?.parseStatus === 'SUCCESS' ? '重新解析' : '解析资料'"
      width="min(540px, calc(100vw - 32px))"
      :close-on-click-modal="!parsing"
      :close-on-press-escape="!parsing"
      :show-close="!parsing"
    >
      <p class="material-parse__target">{{ parseTarget?.title || '当前资料' }}</p>
      <p class="material-parse__desc">按下面的参数把原文切成片段。调整参数后，可以在检索评测里比较效果。</p>
      <el-form label-position="top">
        <el-form-item label="每段最多字符数（200–4000）">
          <el-input-number v-model="parseForm.chunkSize" :min="200" :max="4000" :step="100" :disabled="parsing" />
        </el-form-item>
        <el-form-item label="相邻两段重叠的字符数（最多 500，且小于每段字符数）">
          <el-input-number
            v-model="parseForm.chunkOverlap"
            :min="0"
            :max="Math.min(500, parseForm.chunkSize - 1)"
            :step="20"
            :disabled="parsing"
          />
        </el-form-item>
      </el-form>
      <p v-if="parseTarget?.parseStatus === 'SUCCESS'" class="material-parse__warning">
        重新解析会替换原有片段并清除向量，之后需要重新生成向量。检索评测里按片段编号标注的样本需要重新核对，已有的评测记录不受影响。
      </p>
      <template #footer>
        <el-button :disabled="parsing" @click="parseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="parsing" @click="confirmParseMaterial">开始解析</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="retrievalDialogVisible"
      class="material-library-modal"
      title="检索预览"
      width="min(860px, calc(100vw - 32px))"
      destroy-on-close
    >
      <div class="material-library-form">
        <div class="material-library-form__grid">
          <el-form-item label="资料">
            <el-input :model-value="retrievalForm.materialTitle" disabled />
          </el-form-item>
          <el-form-item label="返回几个片段">
            <el-input-number v-model="retrievalForm.limit" :min="1" :max="10" class="material-library-form__full" />
          </el-form-item>
        </div>
        <el-form-item label="问题">
          <el-input
            v-model="retrievalForm.queryText"
            type="textarea"
            :rows="3"
            placeholder="例如：事务的 ACID 特性是什么？"
          />
        </el-form-item>

        <div class="material-library-dialog__footer">
          <el-button :loading="retrievalLoading" type="primary" @click="runRetrievalPreview">开始检索</el-button>
        </div>

        <div v-if="retrievalLoading" class="state-block" role="status">正在查找相关片段…</div>
        <div v-else-if="retrievalError" class="material-library-detail__empty" role="alert">
          {{ retrievalError }}<el-button @click="runRetrievalPreview">重新检索</el-button>
        </div>
        <div v-else-if="retrievalResult" class="list-stack material-library-retrieval__result">
          <el-alert
            v-if="retrievalResult.degraded"
            type="warning"
            :closable="false"
            show-icon
            title="向量检索暂时不可用，改用了文本检索"
            :description="retrievalResult.degradeReason || '可以稍后再试，或检查向量模型配置。'"
          />
          <el-alert
            v-else-if="retrievalResult.mockEmbedding"
            type="info"
            :closable="false"
            show-icon
            title="当前是演示向量"
            description="演示向量由文字哈希生成，不代表语义相似度，排序主要取决于字面匹配。"
          />
          <div class="soft-text">
            {{ formatRetrievalMode(retrievalResult) }} · 找到 {{ retrievalResult.hitCount }} 个片段
          </div>
          <div v-for="segment in retrievalResult.segments" :key="segment.segmentId" class="section-card">
            <div class="summary-history-card__top">
              <div>
                <div class="section-card__title">
                  {{ segment.sectionTitle || `第 ${segment.segmentNo || '--'} 段` }}
                </div>
                <div class="section-card__meta">
                  {{ formatSegmentMeta(segment) }} · {{ formatSegmentScores(segment) }}
                </div>
              </div>
            </div>
            <div class="summary-block">{{ segment.contentText || '暂无内容' }}</div>
          </div>
          <div v-if="!retrievalResult.segments.length" class="state-block empty">
            没有找到相关片段，换个更具体的问法试试。
          </div>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import MobileIcon from '@/components/MobileIcon.vue'
import MobileFilterToggle from '@/components/MobileFilterToggle.vue'
import MobileContentHub from '@/components/mobile/MobileContentHub.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconMenuButton from '@/components/IconMenuButton.vue'
import MaterialCover from '@/components/resources/MaterialCover.vue'
import MaterialCoverEditor from '@/components/resources/MaterialCoverEditor.vue'
import PdfBookReader from '@/components/resources/PdfBookReader.vue'
import StatusPill from '@/components/StatusPill.vue'
import GenerationModeBadge from '@/components/GenerationModeBadge.vue'
import { resourceReturnPath } from '@/utils/resourceNavigation'
import { knowledgePointPracticeReason } from '@/utils/knowledgePractice'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import { usePolling } from '@/composables/usePolling'
import { useNoteWorkspace } from '@/composables/useNoteWorkspace'
import { confirmDanger } from '@/utils/confirm'
import { getErrorMessage } from '@/utils/errorMessage'
import { formatCharacters, formatDateTime, formatRelativeDay, formatSegmentMeta } from '@/utils/format'
import { DIFFICULTY_LABELS, formatDifficulty, formatMaterialType, formatParseStatus } from '@/utils/labels'
import {
  previewMaterialRetrievalApi,
  submitEmbeddingTaskApi,
  type RetrievalPreviewPayload,
  type RetrievalPreviewSegment
} from '@/api/modules/ai'
import {
  createTextMaterialApi,
  deleteMaterialApi,
  getMaterialDetailApi,
  getMaterialPageApi,
  parseMaterialApi,
  updateMaterialApi,
  uploadMaterialApi,
  type MaterialPageItem
} from '@/api/modules/material'

const router = useRouter()
const route = useRoute()
const { createNote, visible: noteVisible } = useNoteWorkspace()
const allowNoteFocus = (event: Event) => {
  // ElDrawer keeps an internal focus trap even without trap-focus; allow both split panes to receive focus.
  if (noteVisible.value && window.matchMedia('(min-width: 1100px)').matches) event.stopImmediatePropagation()
}
const materialReader = ref<HTMLElement>()
const recordMaterialNote = () => {
  if (!detail.value) return
  const selection = window.getSelection()
  const selected =
    selection?.anchorNode &&
    selection.focusNode &&
    materialReader.value?.contains(selection.anchorNode) &&
    materialReader.value.contains(selection.focusNode)
      ? selection.toString().trim()
      : ''
  createNote({
    sourceType: 'MATERIAL',
    sourceId: detail.value.id,
    sourceTitle: detail.value.title,
    sourceExcerpt: selected.slice(0, 2000)
  })
}
const returnToHome = computed(() => route.query.from === 'home')
const returnToResources = computed(() => route.query.from === 'resources')
const mobileViewport = window.matchMedia('(max-width: 760px)')
const isMobileViewport = ref(mobileViewport.matches)
const updateMobileViewport = (event: MediaQueryListEvent) => {
  isMobileViewport.value = event.matches
}
const showContentHub = computed(
  () =>
    route.query.view !== 'library' && !route.query.keyword && !route.query.materialId && route.query.action !== 'upload'
)
const materials = ref<MaterialPageItem[]>([])
const total = ref(0)
const detail = ref<MaterialDetail | null>(null)
const readerStartPage = ref<number>()
const readingProgress = ref(0)
const documentUrl = computed(() => {
  const url = detail.value?.documentUrl
  return url && /^\/open-books\/[a-z0-9-]+\/book\.pdf$/.test(url) ? url : undefined
})
const readableParagraphs = computed(() => {
  if (documentUrl.value) return []
  const original = detail.value?.contentText
  if (original?.trim()) return [original]
  return (detail.value?.segments || []).map((segment) => segment.contentText?.trim() || '').filter(Boolean)
})
const detailId = ref(0)
const drawerVisible = ref(false)
const uploadDialogVisible = ref(false)
const textDialogVisible = ref(false)
const editDialogVisible = ref(false)
const coverEditorVisible = ref(false)
const coverTarget = ref<MaterialPageItem | null>(null)
const retrievalDialogVisible = ref(false)
const parseDialogVisible = ref(false)
const parsing = ref(false)
const parseTarget = ref<MaterialPageItem | null>(null)
const parseForm = reactive({ chunkSize: 900, chunkOverlap: 140 })
const tableLoading = ref(false)
const detailLoading = ref(false)
const creating = ref(false)
const savingInfo = ref(false)
const uploading = ref(false)
const retrievalLoading = ref(false)
const actionLoadingId = ref<number | null>(null)
const retrievalResult = ref<RetrievalPreviewPayload | null>(null)
const loadError = ref('')
const detailError = ref('')
const retrievalError = ref('')
const editTitleError = ref('')
const editTargetId = ref<number | null>(null)
let listRequestVersion = 0
let detailRequestVersion = 0

interface MaterialDetail extends MaterialPageItem {
  resourceId?: number | null
  documentUrl?: string | null
  contentText?: string | null
  segments?: { id: number; segmentNo?: number; pageNo?: number; sectionTitle?: string; contentText?: string }[]
}

const materialTypes = [
  { value: 'PDF', label: 'PDF' },
  { value: 'DOCX', label: 'Word' },
  { value: 'TXT', label: 'TXT 文件' },
  { value: 'TEXT', label: '纯文本' },
  { value: 'MARKDOWN', label: 'Markdown' },
  { value: 'FILE', label: '其他格式' }
]

const readQueryText = (value: unknown) => (typeof value === 'string' ? value : '')

const form = reactive({
  title: '',
  materialType: 'TEXT',
  difficultyLevel: 3,
  tags: '',
  description: '',
  contentText: ''
})

const editForm = reactive({
  title: '',
  tags: '',
  difficultyLevel: 3,
  description: ''
})

const filters = reactive({
  keyword: readQueryText(route.query.keyword),
  materialType: '',
  parseStatus: ''
})

const searchKeyword = computed({
  get: () => filters.keyword,
  set: (keyword: string) => {
    filters.keyword = keyword
    if (route.path !== '/materials') return
    const query = { ...route.query }
    query.view = 'library'
    if (keyword) query.keyword = keyword
    else delete query.keyword
    void router.replace({ path: route.path, query, hash: route.hash })
  }
})

const page = reactive({
  current: 1,
  size: 10
})

const hasActiveFilters = computed(() => Boolean(filters.keyword.trim() || filters.materialType || filters.parseStatus))
const mobileFiltersOpen = ref(false)
const mobileFilterCount = computed(() => [filters.materialType, filters.parseStatus].filter(Boolean).length)

const autoQuery = useAutoListQuery(
  [() => filters.keyword, () => filters.materialType, () => filters.parseStatus],
  () => {
    page.current = 1
    return loadMaterials()
  }
)

const retrievalForm = reactive({
  materialId: 0,
  materialTitle: '',
  queryText: '',
  limit: 3
})

const getTags = (value?: string) =>
  Array.from(
    new Set(
      (value || '')
        .split(/[,，、;；|\n]+/)
        .map((tag) => tag.trim())
        .filter(Boolean)
    )
  )

/** 每行只解析一次标签，模板里直接用 row.tagList。 */
const materialRows = computed(() => materials.value.map((row) => ({ ...row, tagList: getTags(row.tags) })))

const formatSummaryStatus = (value?: string) =>
  ({
    SUCCESS: '已生成',
    PENDING: '还没有',
    PROCESSING: '生成中',
    RUNNING: '生成中',
    FAILED: '生成失败'
  })[String(value || '').toUpperCase()] || '还没有'

const isProcessing = (row: MaterialPageItem) => {
  return row.parseStatus === 'PROCESSING' || row.summaryStatus === 'RUNNING' || row.embeddingStatus === 'RUNNING'
}

const getProcessingText = (row: MaterialPageItem) => {
  if (row.parseStatus === 'PROCESSING') {
    return '解析中…'
  }
  if (row.summaryStatus === 'RUNNING') {
    return '总结生成中…'
  }
  if (row.embeddingStatus === 'RUNNING') {
    return '向量生成中…'
  }
  return '处理中…'
}

const formatScore = (value?: number | null) => (typeof value === 'number' ? value.toFixed(3) : '--')

const formatRetrievalMode = (result: RetrievalPreviewPayload) =>
  result.degraded || result.retrievalMode === 'TEXT' ? '文本检索' : '向量检索 + 词面重排'

/** 文本检索没有向量分，只展示词面匹配度；向量检索同时展示综合、向量和词面三项得分。 */
const formatSegmentScores = (segment: RetrievalPreviewSegment) =>
  segment.semanticScore === null || segment.semanticScore === undefined
    ? `文本匹配度 ${formatScore(segment.lexicalScore)}`
    : `综合 ${formatScore(segment.score)} · 向量 ${formatScore(segment.semanticScore)} · 词面 ${formatScore(segment.lexicalScore)}`

const formatEmbeddingStatus = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SUCCESS':
      return '已完成'
    case 'RUNNING':
      return '生成中'
    case 'PARTIAL':
      return '部分完成'
    case 'PARTIAL_FAILED':
      return '部分失败'
    case 'FAILED':
      return '失败'
    case 'PARSING':
      return '解析中'
    case 'PARSE_FAILED':
      return '解析失败'
    case 'PENDING':
      return '未生成'
    case 'NOT_READY':
    default:
      return '待解析'
  }
}

const formatEmbeddingProgress = (row: MaterialPageItem) => {
  const embedded = Number(row.embeddedSegmentCount || 0)
  const total = Number(row.totalSegmentCount || 0)
  if (total > 0) {
    return `${embedded} / ${total} 个片段`
  }
  const parseStatus = String(row.parseStatus || '').toUpperCase()
  if (parseStatus === 'PROCESSING') {
    return '解析完成后可以生成'
  }
  if (parseStatus === 'FAILED') {
    return '解析失败，无法生成'
  }
  if (parseStatus === 'SUCCESS') return '没有片段'
  return '先解析资料'
}

const shouldRegenerateEmbedding = (row: MaterialPageItem) =>
  Number(row.embeddedSegmentCount || 0) > 0 ||
  ['SUCCESS', 'PARTIAL', 'PARTIAL_FAILED', 'FAILED'].includes(String(row.embeddingStatus || '').toUpperCase())

const getEmbeddingActionLabel = (row: MaterialPageItem) =>
  shouldRegenerateEmbedding(row) ? '重新生成向量' : '生成向量'

const resetForm = () => {
  form.title = ''
  form.materialType = 'TEXT'
  form.difficultyLevel = 3
  form.tags = ''
  form.description = ''
  form.contentText = ''
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    searchKeyword.value = ''
    filters.materialType = ''
    filters.parseStatus = ''
    page.current = 1
  })
}

const loadMaterials = async (silentOrEvent?: boolean | unknown) => {
  const silent = silentOrEvent === true
  const requestVersion = ++listRequestVersion
  if (!silent) tableLoading.value = true
  loadError.value = ''
  try {
    const res = await getMaterialPageApi({
      current: page.current,
      size: page.size,
      keyword: filters.keyword.trim() || undefined,
      materialType: filters.materialType || undefined,
      parseStatus: filters.parseStatus || undefined
    })
    if (requestVersion !== listRequestVersion) return
    materials.value = res.data.data.records || []
    total.value = res.data.data.total || 0
    const lastPage = Math.max(1, Math.ceil(total.value / page.size))
    if (page.current > lastPage) {
      page.current = lastPage
      await loadMaterials()
    }
  } catch (error) {
    if (requestVersion === listRequestVersion && !silent) loadError.value = getErrorMessage(error, '请稍后再试。')
  } finally {
    if (requestVersion === listRequestVersion && !silent) tableLoading.value = false
  }
}

// 有资料在解析、生成总结或生成向量时，每 3 秒静默刷新一次列表，全部完成后自动停止。
const hasProcessingMaterials = computed(() => materials.value.some((item) => isProcessing(item)))
usePolling(hasProcessingMaterials, () => loadMaterials(true), 3000)

const createMaterial = async () => {
  if (!form.title.trim() || !form.contentText.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }

  creating.value = true
  try {
    await createTextMaterialApi({
      ...form,
      title: form.title.trim(),
      tags: form.tags.trim(),
      description: form.description.trim(),
      contentText: form.contentText.trim()
    })
    ElMessage.success('资料已保存')
    resetForm()
    textDialogVisible.value = false
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '资料没有保存成功，请稍后重试。'))
  } finally {
    creating.value = false
  }
}

const formatChunking = (material: MaterialPageItem) =>
  material.chunkStrategy === 'CHAR_WINDOW_V2'
    ? `每段 ${material.chunkSize} 字符，重叠 ${material.chunkOverlap} 字符`
    : material.chunkStrategy === 'ORIGINAL_CONTEXT'
      ? '按原文段落'
      : '未记录'

const parseMaterial = (id: number) => {
  if (parsing.value) return
  parseTarget.value = materials.value.find((item) => item.id === id) || (detail.value?.id === id ? detail.value : null)
  if (!parseTarget.value) return
  parseForm.chunkSize = parseTarget.value.chunkSize || 900
  parseForm.chunkOverlap = parseTarget.value.chunkOverlap ?? 140
  parseDialogVisible.value = true
}

const confirmParseMaterial = async () => {
  if (!parseTarget.value || parsing.value) return
  if (
    !Number.isInteger(parseForm.chunkSize) ||
    !Number.isInteger(parseForm.chunkOverlap) ||
    parseForm.chunkSize < 200 ||
    parseForm.chunkSize > 4000 ||
    parseForm.chunkOverlap < 0 ||
    parseForm.chunkOverlap > 500 ||
    parseForm.chunkOverlap >= parseForm.chunkSize
  ) {
    ElMessage.warning('请检查分段参数：重叠字符数要小于每段字符数')
    return
  }
  const id = parseTarget.value.id
  parsing.value = true
  actionLoadingId.value = id
  try {
    await parseMaterialApi(id, { ...parseForm })
    ElMessage.success('解析完成')
    parseDialogVisible.value = false
    await loadMaterials()
    if (drawerVisible.value && detailId.value === id) await viewDetail(id)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '解析没有完成，请稍后重试。'))
    await loadMaterials()
    if (drawerVisible.value && detailId.value === id) await viewDetail(id)
  } finally {
    parsing.value = false
    actionLoadingId.value = null
  }
}

const generateEmbedding = async (row: MaterialPageItem) => {
  if (actionLoadingId.value === row.id) return
  if (row.parseStatus !== 'SUCCESS') {
    ElMessage.warning('请先解析资料，再生成向量')
    return
  }
  if (row.embeddingStatus === 'RUNNING') {
    ElMessage.info('向量正在生成，可以在任务中心查看进度')
    return
  }

  const forceRegenerate = shouldRegenerateEmbedding(row)
  if (forceRegenerate) {
    const confirmed = await confirmDanger(
      `会用当前配置的向量模型重新生成“${row.title}”的全部向量，原有向量会被替换。`,
      { title: '重新生成向量', confirmText: '重新生成' }
    )
    if (!confirmed) return
  }

  actionLoadingId.value = row.id
  try {
    await submitEmbeddingTaskApi(row.id, { forceRegenerate })
    ElMessage.success('已开始生成向量，可以在任务中心查看进度')
    await loadMaterials()
    if (drawerVisible.value && detailId.value === row.id) await viewDetail(row.id)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '向量没有开始生成，请稍后重试。'))
  } finally {
    actionLoadingId.value = null
  }
}

const removeMaterial = async (id: number) => {
  if (!(await confirmDanger('删除后，资料和解析出的片段都无法恢复。确定删除吗？'))) return

  actionLoadingId.value = id
  try {
    await deleteMaterialApi(id)
    ElMessage.success('资料已删除')
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '没有删除成功，请稍后重试。'))
  } finally {
    actionLoadingId.value = null
  }
}

const viewDetail = async (id: number) => {
  if (!Number.isSafeInteger(id) || id <= 0) return
  const requestVersion = ++detailRequestVersion
  detailId.value = id
  detail.value = null
  readerStartPage.value = undefined
  readingProgress.value = 0
  detailError.value = ''
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getMaterialDetailApi(id)
    if (requestVersion !== detailRequestVersion) return
    detail.value = res.data.data
    if (!detail.value) detailError.value = '这份资料不存在或已经删除了。'
  } catch (error) {
    if (requestVersion !== detailRequestVersion) return
    detail.value = null
    detailError.value = getErrorMessage(error, '资料详情没有加载出来，请重试。')
  } finally {
    if (requestVersion === detailRequestVersion) detailLoading.value = false
  }
}

const openEditDialog = (row: MaterialPageItem) => {
  editTargetId.value = row.id
  editForm.title = row.title
  editForm.tags = getTags(row.tags).join('、')
  editForm.difficultyLevel = row.difficultyLevel || 3
  editForm.description = row.description || ''
  editTitleError.value = ''
  editDialogVisible.value = true
}

const saveMaterialInfo = async () => {
  const id = editTargetId.value
  if (!id || savingInfo.value) return
  const title = editForm.title.trim()
  if (!title) {
    editTitleError.value = '标题不能为空'
    return
  }
  const payload = {
    title,
    tags: editForm.tags.trim(),
    difficultyLevel: editForm.difficultyLevel,
    description: editForm.description.trim()
  }
  savingInfo.value = true
  try {
    await updateMaterialApi(id, payload)
    ElMessage.success('资料信息已更新')
    editDialogVisible.value = false
    if (detail.value?.id === id) {
      detail.value = { ...detail.value, ...payload, tags: getTags(payload.tags).join(',') }
    }
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '没有保存成功，请稍后重试。'))
  } finally {
    savingInfo.value = false
  }
}

const openRetrievalPreview = (row: MaterialPageItem) => {
  retrievalDialogVisible.value = true
  retrievalResult.value = null
  retrievalError.value = ''
  retrievalForm.materialId = row.id
  retrievalForm.materialTitle = row.title
  retrievalForm.queryText = ''
  retrievalForm.limit = 3
}

const runRetrievalPreview = async () => {
  if (!retrievalForm.materialId) {
    ElMessage.warning('请先选择资料')
    return
  }
  if (!retrievalForm.queryText.trim()) {
    ElMessage.warning('请输入问题')
    return
  }

  retrievalLoading.value = true
  retrievalError.value = ''
  try {
    const res = await previewMaterialRetrievalApi(retrievalForm.materialId, {
      queryText: retrievalForm.queryText.trim(),
      limit: retrievalForm.limit
    })
    retrievalResult.value = res.data.data as RetrievalPreviewPayload
    if (!retrievalResult.value.segments.length) {
      ElMessage.info('没有找到相关片段，换个更具体的问法试试')
    }
  } catch (error) {
    retrievalResult.value = null
    retrievalError.value = getErrorMessage(error, '检索没有完成，请稍后重试。')
  } finally {
    retrievalLoading.value = false
  }
}

const uploadMaterial = async (options: UploadRequestOptions) => {
  uploading.value = true
  try {
    const response = await uploadMaterialApi(options.file as File)
    uploadDialogVisible.value = false
    options.onSuccess?.({})
    await loadMaterials()
    const uploadedId = Number(response.data.data)
    const uploaded = materialRows.value.find((row) => row.id === uploadedId)
    if (uploaded?.parseStatus === 'FAILED') {
      ElMessage.warning(uploaded.parseErrorMessage || '文件已上传，但解析没有成功，打开详情可以看到原因。')
    } else {
      ElMessage.success('上传完成')
    }
  } catch (error) {
    options.onError?.(error as Parameters<UploadRequestOptions['onError']>[0])
    ElMessage.error(getErrorMessage(error, '上传失败，请稍后重试。'))
  } finally {
    uploading.value = false
  }
}

const beforeUpload = (file: File) => {
  if (uploading.value) return false
  if (!/\.(pdf|docx|txt)$/i.test(file.name)) {
    ElMessage.warning('请选择 PDF、Word（.docx）或 TXT 文件')
    return false
  }
  return true
}

const handleMaterialCommand = (command: string, row: MaterialPageItem) => {
  switch (command) {
    case 'cover':
      openCoverEditor(row)
      break
    case 'edit':
      openEditDialog(row)
      break
    case 'parse':
      void parseMaterial(row.id)
      break
    case 'embedding':
      void generateEmbedding(row)
      break
    case 'retrieval':
      openRetrievalPreview(row)
      break
    case 'delete':
      void removeMaterial(row.id)
      break
  }
}

function openCoverEditor(material: MaterialPageItem) {
  coverTarget.value = material
  coverEditorVisible.value = true
}

function updateCover({ id, coverUrl }: { id: number; coverUrl: string | null }) {
  materials.value = materials.value.map((item) => (item.id === id ? { ...item, coverUrl } : item))
  if (detail.value?.id === id) detail.value.coverUrl = coverUrl
  if (coverTarget.value?.id === id) coverTarget.value = { ...coverTarget.value, coverUrl }
  // 新请求使保存前的轮询响应失效，避免旧封面覆盖刚保存的结果。
  void loadMaterials(!tableLoading.value)
  if (detailLoading.value && detailId.value === id) void viewDetail(id)
}

const returnToOriginAfterClose = () => {
  if (route.path !== '/materials') return
  if (returnToHome.value) void router.replace('/dashboard')
  else if (returnToResources.value) void router.replace(resourceReturnPath(route.query))
}

// 用户退出与上传成功后的自动关闭分开处理，避免上传完成时意外离开资料库。
const closeUploadDialog = (done?: () => void) => {
  if (uploading.value) return
  if (done) done()
  else uploadDialogVisible.value = false
  returnToOriginAfterClose()
}

const closeMaterialDetail = (done?: () => void) => {
  if (done) done()
  else drawerVisible.value = false
  returnToOriginAfterClose()
}

const clearMaterialDeepLink = () => {
  if (route.path !== '/materials' || returnToHome.value || returnToResources.value || !route.query.materialId) return
  const query = { ...route.query }
  query.view = 'library'
  delete query.materialId
  void router.replace({ path: route.path, query })
}

const goSummary = (id: number) => {
  router.push({ path: '/summary', query: { materialId: String(id) } })
}

const goQuiz = (id: number) => {
  const originalMaterialId = Number(route.query.practiceMaterialId)
  const knowledgePoint = returnToResources.value ? readQueryText(route.query.knowledgePoint).trim() : ''
  const hasValidKnowledgePoint = !knowledgePointPracticeReason(knowledgePoint)
  const useOriginal = hasValidKnowledgePoint && Number.isSafeInteger(originalMaterialId) && originalMaterialId > 0
  router.push({
    path: '/quiz',
    query: {
      materialId: String(useOriginal ? originalMaterialId : id),
      generate: '1',
      ...(hasValidKnowledgePoint ? { knowledgePoint } : {})
    }
  })
}

watch(
  () => route.query.keyword,
  (value) => {
    if (route.path !== '/materials') return
    const keyword = readQueryText(value)
    if (keyword !== filters.keyword) {
      autoQuery.runAfterMutation(() => {
        filters.keyword = keyword
        page.current = 1
      })
    }
  }
)

watch(
  () => route.query.materialId,
  (value) => {
    if (route.path !== '/materials') return
    const rawId = readQueryText(value)
    if (!rawId) return
    const id = Number(rawId)
    if (!/^[1-9]\d*$/.test(rawId) || !Number.isSafeInteger(id)) {
      ElMessage.warning('这个资料链接无效，请从资料库重新打开')
      return
    }
    void viewDetail(id)
  },
  { immediate: true }
)

watch(
  () => route.query.action,
  (value) => {
    if (route.path !== '/materials' || value !== 'upload') return
    uploadDialogVisible.value = true
    const query = { ...route.query }
    query.view = 'library'
    delete query.action
    void router.replace({ path: route.path, query })
  },
  { immediate: true }
)

onMounted(() => {
  mobileViewport.addEventListener('change', updateMobileViewport)
})

onUnmounted(() => {
  mobileViewport.removeEventListener('change', updateMobileViewport)
  listRequestVersion += 1
  detailRequestVersion += 1
})

void loadMaterials()
</script>

<style scoped>
.material-note-button {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.material-note-button:hover {
  color: var(--brand);
  background: var(--brand-light);
}
.material-note-button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
:global(:root .el-drawer__header.material-library-detail__header) {
  padding: 6px 12px;
}
:global(.material-library-modal--book .el-drawer__header) {
  margin-bottom: 0;
}
:global(.material-library-modal--book .el-drawer__body) {
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
@media (min-width: 1100px) {
  :global(body.note-split-open .el-drawer.material-library-modal) {
    right: 460px;
    max-width: calc(100% - 460px);
  }
}
.material-library-detail__navigation {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 44px;
  align-items: center;
  width: 100%;
}
.material-library-detail__title {
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
  text-align: center;
}
.material-library-detail__back {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text);
  cursor: pointer;
}
.material-library-detail__back:active {
  background: var(--bg-secondary);
}
.material-library-detail__back:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
.material-row__error {
  margin: 4px 0 0;
  color: var(--red);
  font-size: 13px;
  overflow-wrap: anywhere;
}
.material-row__summary :deep(.generation-mode-badge) {
  margin-left: 6px;
  padding: 0 7px;
  font-size: 11px;
}
.material-library__spinner {
  display: inline-block;
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  border: 2px solid var(--line);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: zx-spin 0.8s linear infinite;
}
.material-library-upload :deep(.el-upload),
.material-library-upload :deep(.el-upload-dragger) {
  display: block;
  width: 100%;
}
.material-library-upload :deep(.el-upload-dragger) {
  padding: 36px 20px 32px;
  border: 1px dashed color-mix(in srgb, var(--brand) 35%, var(--line-strong));
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--brand) 3%, var(--panel));
  transition:
    border-color var(--duration-fast) ease,
    background-color var(--duration-fast) ease;
}
.material-library-upload :deep(.el-upload-dragger:hover),
.material-library-upload :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--brand);
  background: var(--brand-light);
}
.material-library-upload__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin: 0 auto 14px;
  border-radius: 14px;
  background: var(--brand-soft);
  color: var(--brand);
}
.material-library-upload strong {
  display: block;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.material-library-upload strong + span {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  font-size: 13px;
}
.material-library-upload .material-library__spinner {
  margin-top: 16px;
}
.material-library-upload__note {
  margin: 12px 2px 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.material-library-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.material-library-form__full {
  width: 100%;
}
.material-library-dialog__footer {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}
.material-library-dialog__footer .el-button {
  margin: 0;
}
.material-library-retrieval__result {
  margin-top: 18px;
}
.material-library-detail {
  min-width: 0;
}
.material-library-detail--book {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.material-library-detail--book .material-library-detail__intro {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px 12px;
  flex: none;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.material-library-detail--book .material-library-detail__heading {
  grid-column: 1;
  grid-row: 1;
  gap: 10px;
  min-width: 0;
}
.material-library-detail--book .material-detail-cover {
  --resource-cover-width: 36px;
  padding: 4px;
}
.material-library-detail--book .material-library-detail__heading h2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  margin-top: 0;
  font-size: 16px;
  line-height: 1.5;
}
.material-library-detail--book .material-library-detail__study-actions {
  grid-column: 2;
  grid-row: 1;
  flex-wrap: nowrap;
  gap: 6px;
  margin: 0;
}
.material-library-detail--book .material-library-detail__action-label {
  display: none;
}
.material-library-detail--book .material-library-detail__study-actions .el-button[aria-label] {
  width: 44px;
  min-width: 44px;
  height: 44px;
  min-height: 44px;
  padding: 0;
}
.material-library-detail--book .material-library-detail__intro :deep(.el-alert) {
  grid-column: 1 / -1;
  margin: 0;
}
.material-library-detail__book {
  flex: 1;
  height: auto;
  min-height: 0;
}
.material-library-detail__progress {
  flex: none;
  margin: 0;
  padding: 3px 12px max(6px, env(safe-area-inset-bottom));
  color: var(--muted);
  font-size: 10px;
  line-height: 1.5;
  text-align: center;
}
.material-library-detail__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  min-height: 320px;
  color: var(--muted);
  font-size: 14px;
}
.material-library-detail__loading p {
  margin: 0;
}
.material-library-detail__heading {
  display: flex;
  align-items: center;
  gap: 14px;
}
.material-library .record-row {
  --record-lead: 60px;
}
.material-row__cover,
.material-detail-cover {
  --resource-cover-width: 60px;
  flex: 0 0 auto;
  display: block;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: none;
  cursor: pointer;
}
.material-detail-cover {
  --resource-cover-width: 72px;
  position: relative;
}
.material-detail-cover__edit {
  position: absolute;
  right: -5px;
  bottom: -5px;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 2px solid var(--panel);
  border-radius: 50%;
  background: var(--bg-secondary);
  color: var(--text-secondary);
}
.material-row__cover:focus-visible,
.material-detail-cover:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 4px;
}
.material-library-detail__heading > div {
  min-width: 0;
}
.material-library-detail__brief {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__heading h2 {
  margin: 2px 0 0;
  color: var(--text);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.material-library-detail__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 20px;
  margin: 20px 0;
  padding: 16px 18px;
  border-radius: var(--radius);
  background: var(--bg);
}
.material-library-detail__meta div {
  min-width: 0;
}
.material-library-detail__meta dt {
  color: var(--muted);
  font-size: 12px;
}
.material-library-detail__meta dd {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 2px 0 0;
  color: var(--text);
  font-size: 14px;
  overflow-wrap: anywhere;
}
.material-library-detail__meta .material-library-detail__meta-wide {
  grid-column: 1 / -1;
}
.material-library-detail__meta dd.is-empty {
  color: var(--muted);
}
.material-library-detail__tools {
  margin-top: 24px;
  border-top: 1px solid var(--line);
}
.material-library-detail__tools > summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  color: var(--text-secondary);
  font-size: 14px;
  list-style: none;
  cursor: pointer;
}
.material-library-detail__tools > summary::-webkit-details-marker {
  display: none;
}
.material-library-detail__tools > summary:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
  border-radius: 4px;
}
.material-library-detail__tools[open] > summary > svg {
  transform: rotate(180deg);
}
.material-library-detail__study-actions {
  margin-top: 20px;
}
.material-library-detail :deep(.el-alert) {
  margin-block: 16px;
}
.material-library-detail__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.material-library-detail__actions .el-button {
  margin: 0;
}
.material-library-detail__retrieval {
  margin-top: 20px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.material-library-detail__retrieval-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 14px;
}
.material-library-detail__retrieval h3 {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}
.material-library-detail__retrieval p {
  margin: 4px 0 14px;
  color: var(--muted);
  font-size: 13px;
}
.material-library-detail__retrieval a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--brand);
  font-size: 13px;
  text-decoration: none;
}
.material-library-detail__retrieval a:hover {
  color: var(--brand-hover);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.material-library-detail__body {
  margin-top: 24px;
}
.material-library-detail__body p {
  margin: 0;
  color: var(--text);
  font-size: 16px;
  line-height: 1.9;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.material-library-detail__body p + p {
  margin-top: 1em;
}
.material-library-detail__empty {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
  padding: 20px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  color: var(--muted);
  font-size: 14px;
}
.material-parse__target {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.material-parse__desc {
  margin: 4px 0 18px;
  color: var(--muted);
  font-size: 13px;
}
.material-parse__warning {
  margin: 4px 0 0;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--accent-soft);
  color: var(--text-secondary);
  font-size: 13px;
}
@media (max-width: 640px) {
  .material-library-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 760px) {
  .material-library .record-row:not(.record-row--loading) {
    display: grid;
    grid-template-columns: 60px minmax(0, 1fr);
    gap: 6px 12px;
  }
  .material-library .material-row__cover {
    grid-row: 1 / span 2;
  }
  .material-library .record-row__actions {
    grid-column: 2;
    min-width: 0;
    padding-left: 0;
    flex-wrap: wrap;
  }
  .material-library-upload__note {
    display: none;
  }
  .material-library-detail__heading {
    align-items: flex-start;
  }
  .material-library-detail__heading h2 {
    margin-top: 0;
    font-size: 22px;
    line-height: 1.5;
  }
  .material-library-detail__study-actions {
    display: flex;
    gap: 10px;
    margin-top: 14px;
  }
  .material-library-detail .material-library-detail__study-actions .el-button[aria-label] {
    width: 44px;
    min-width: 44px;
    height: 44px;
    min-height: 44px;
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: color-mix(in srgb, var(--brand) 5%, var(--panel));
    color: var(--text-secondary);
  }
  .material-library-detail .material-library-detail__study-actions .el-button[aria-label]:hover:not(.is-disabled) {
    background: color-mix(in srgb, var(--brand) 10%, var(--panel));
  }
  .material-library-detail .material-library-detail__study-actions .el-button[aria-label].is-disabled {
    background: var(--bg-secondary);
    color: var(--muted);
    opacity: 0.45;
  }
  .material-library-detail__study-actions .material-library-detail__action-label {
    display: none;
  }
  .material-library-detail__meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: 8px 0 16px;
    margin-block: 0 8px;
    gap: 14px;
    border: 0;
    border-radius: 0;
    background: transparent;
  }
  .material-library-detail__meta dd {
    margin-top: 4px;
    font-size: 13px;
    line-height: 1.7;
  }
  .material-library-detail__meta-wide dd {
    font-size: 13px;
    line-height: 1.7;
  }
  .material-library-detail__retrieval {
    margin-top: 20px;
    padding: 16px 0 0;
    border: 0;
    border-top: 1px solid var(--line);
    border-radius: 0;
  }
  .material-library-detail__retrieval h3 {
    font-size: 15px;
    line-height: 1.5;
  }
  .material-library-detail__retrieval p {
    margin-top: 8px;
    font-size: 14px;
    line-height: 1.85;
  }
  .material-library-retrieval__result .summary-block {
    font-size: 16px;
    line-height: 1.85;
  }
  .material-library-retrieval__result {
    gap: 0;
  }
  .material-library-retrieval__result > .section-card {
    padding: 24px 0;
    border: 0;
    border-top: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
  }
  .material-library-retrieval__result .section-card__title {
    font-size: 20px;
    line-height: 1.6;
  }
  .material-library-retrieval__result .summary-history-card__top {
    flex-wrap: wrap;
  }
}
@media (min-width: 761px) {
  .material-library-detail .material-library-detail__study-actions .el-button[aria-label] {
    width: 44px;
    min-width: 44px;
    height: 44px;
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--bg-secondary);
    color: var(--text-secondary);
    box-shadow: none;
  }
  .material-library-detail .material-library-detail__study-actions .el-button[aria-label]:hover:not(.is-disabled) {
    background: var(--brand-light);
  }
  .material-library-detail__study-actions .material-library-detail__action-label {
    display: none;
  }
  .material-library {
    max-width: 1120px;
  }
  .material-library > .page-header .page-desc {
    display: none;
  }
  .material-library > .workspace-panel {
    overflow: visible;
    border: 0;
    border-radius: 0;
    background: transparent;
  }
  .material-library .workspace-toolbar {
    margin-bottom: 16px;
    padding: 12px 16px;
    border: 1px solid var(--line);
    border-radius: 16px;
    background: var(--panel);
  }
  .material-library .record-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
  .material-library .record-row {
    --record-lead: 60px;
    display: grid;
    grid-template-columns: var(--record-lead) minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) auto;
    gap: 14px;
    min-width: 0;
    padding: 18px;
    border: 1px solid var(--line);
    border-radius: 18px;
    background: var(--panel);
  }
  .material-library .record-row:hover {
    border-color: var(--line-strong);
  }
  .material-library .record-row__main {
    grid-column: 2;
  }
  .material-library .record-row__title {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 6px;
    font-size: 16px;
  }
  .material-library .record-row__title > :first-child {
    display: -webkit-box;
    flex: 1 1 100%;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    white-space: normal;
    overflow-wrap: anywhere;
  }
  .material-library .record-row__meta {
    margin-top: 8px;
    row-gap: 6px;
  }
  .material-library .record-row__actions {
    grid-column: 1 / -1;
    align-self: end;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: 6px;
    min-width: 0;
    padding: 12px 0 0;
    border-top: 1px solid var(--line);
  }
  .material-library .record-row__actions > .el-dropdown {
    margin-left: auto;
  }
  .material-library .record-row__actions > .el-button,
  .material-library .record-row__actions :deep(.icon-menu-button) {
    min-height: 44px;
    border-radius: 11px;
  }
  .material-library .record-row__sk-actions {
    grid-column: 1 / -1;
    width: 160px;
    margin-top: 12px;
  }
  .material-library .workspace-pagination {
    padding: 18px 0 0;
    border: 0;
  }
  .material-library .workspace-progress {
    margin-bottom: 16px;
    border: 0;
    border-radius: 12px;
  }
}
@media (min-width: 761px) and (max-width: 980px) {
  .material-library .record-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
