<template>
  <section class="learning-page rag-eval-view" aria-labelledby="rag-eval-title">
    <header class="page-header">
      <div>
        <h1 id="rag-eval-title" class="page-title">检索评测</h1>
        <p class="page-desc">用标注好的问题比较文本、向量和重排方案，查看命中情况及每次运行的差异。</p>
      </div>
      <div class="toolbar toolbar--flush">
        <el-button type="primary" @click="openDatasetDialog"><AppIcon name="plus" :size="16" />新建评测集</el-button>
        <el-button @click="openCmrcDialog">导入 CMRC2018</el-button>
      </div>
    </header>

    <div class="workspace-panel">
      <div class="workspace-toolbar">
        <div class="filter-bar" role="search" aria-label="筛选评测集">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索评测集名称或说明"
            aria-label="搜索评测集名称或说明"
            class="workspace-filter-bar__search"
            ><template #prefix><AppIcon name="search" :size="16" /></template
          ></el-input>
          <el-select
            v-model="filters.materialId"
            clearable
            filterable
            placeholder="全部资料"
            aria-label="按关联资料筛选"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · #${item.id}`"
              :value="item.id"
            />
          </el-select>
          <el-button v-if="hasFilters" text @click="resetFilters">清除筛选</el-button>
        </div>

        <el-button text :loading="datasetLoading" @click="loadDatasets"
          ><AppIcon v-if="!datasetLoading" name="refresh" :size="16" />刷新</el-button
        >
      </div>

      <div class="workspace-body rag-eval-layout">
        <div class="page-card workspace-card">
          <div class="workspace-card__header workspace-card__header--spaced">
            <div>
              <h2 class="rag-section-title">
                评测集 <span v-if="!datasetError">{{ datasetTotal }}</span>
              </h2>
              <p>选择一个评测集，管理样本或查看运行记录。</p>
            </div>
          </div>

          <div v-if="datasetLoading" class="record-list" role="status" aria-label="正在加载评测集">
            <div v-for="i in 3" :key="i" class="list-row record-row record-row--loading" aria-hidden="true">
              <div class="record-row__main">
                <span class="skeleton record-row__sk-title" /><span class="skeleton record-row__sk-meta" />
              </div>
              <span class="skeleton record-row__sk-actions" />
            </div>
          </div>
          <EmptyState v-else-if="datasetError" variant="error" title="评测集没有加载出来" :description="datasetError">
            <el-button type="primary" @click="loadDatasets">重新加载</el-button>
          </EmptyState>
          <EmptyState
            v-else-if="!datasets.length"
            icon="search"
            :title="hasFilters ? '没有找到匹配的评测集' : '还没有评测集'"
            :description="
              hasFilters
                ? '换个关键词，或者清除筛选条件再看看。'
                : '选择一份资料，新建评测集后添加问题和对应的原文片段。'
            "
          >
            <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
            <el-button v-else type="primary" @click="openDatasetDialog">新建评测集</el-button>
          </EmptyState>
          <div v-else class="workspace-table">
            <div class="workspace-table__head workspace-table__head--rag-eval">
              <span>评测集</span>
              <span>资料</span>
              <span>样本</span>
              <span>最近运行</span>
              <span>操作</span>
            </div>

            <div
              v-for="item in datasets"
              :key="item.id"
              class="workspace-table__row workspace-table__row--rag-eval"
              :class="{ 'workspace-table__row--active': selectedDataset?.id === item.id }"
            >
              <div class="workspace-table__title">
                <strong>{{ item.name }}</strong>
                <span>#{{ item.id }} · {{ item.description || '暂无说明' }}</span>
              </div>
              <div class="task-meta-stack" data-label="资料">
                <strong>{{ item.materialTitle || `资料 #${item.materialId}` }}</strong>
                <span>资料 ID #{{ item.materialId }}</span>
              </div>
              <span data-label="样本">{{ item.sampleCount || 0 }} 条</span>
              <div class="task-meta-stack" data-label="最近运行">
                <strong>{{ item.lastRunId ? `#${item.lastRunId}` : '未运行' }}</strong>
                <span>{{ formatDateTime(item.lastRunAt) }}</span>
              </div>
              <div class="workspace-action-row workspace-action-row--fit" data-label="操作">
                <el-button link type="primary" @click="selectDataset(item)">管理样本</el-button>
                <el-button link :disabled="!item.sampleCount || runningEval" @click="runDataset(item)"
                  >运行评测</el-button
                >
                <el-button link @click="selectDataset(item)">运行历史</el-button>
                <el-button link type="danger" @click="removeDataset(item)">删除</el-button>
              </div>
            </div>
          </div>

          <div v-if="!datasetError && datasetTotal" class="workspace-pagination">
            <div class="workspace-pagination__meta">
              第 {{ datasetPage.current }} / {{ Math.max(1, Math.ceil(datasetTotal / datasetPage.size)) }} 页
            </div>
            <el-pagination
              v-model:current-page="datasetPage.current"
              v-model:page-size="datasetPage.size"
              :page-sizes="[10, 20, 50]"
              :pager-count="5"
              layout="sizes, prev, pager, next"
              :total="datasetTotal"
              @current-change="loadDatasets"
              @size-change="loadDatasets"
            />
          </div>
        </div>

        <div class="page-card workspace-card rag-eval-side">
          <div class="workspace-card__header workspace-card__header--spaced">
            <div>
              <h2 class="rag-section-title">样本标注</h2>
              <p v-if="selectedDataset">
                当前评测集：{{ selectedDataset.name }}，基于
                {{ selectedDataset.materialTitle || `资料 #${selectedDataset.materialId}` }}。
              </p>
              <p v-else>选择上方的评测集后，在这里添加问题和相关片段。</p>
            </div>
            <div class="toolbar toolbar--flush">
              <el-button :disabled="!selectedDataset" @click="openSegmentDrawer">资料分段参考</el-button>
              <el-button :disabled="!selectedDataset" @click="openBatchDialog">批量导入</el-button>
              <el-button type="primary" :disabled="!selectedDataset" @click="openSampleDialog()">新增样本</el-button>
            </div>
          </div>

          <EmptyState
            v-if="!selectedDataset"
            icon="quiz"
            title="先选择一个评测集"
            description="点击上方评测集的“管理样本”，即可查看和编辑标注。"
          />
          <div v-else-if="sampleLoading" class="state-block" role="status">正在加载评测样本…</div>
          <EmptyState v-else-if="sampleError" variant="error" title="样本没有加载出来" :description="sampleError"
            ><el-button type="primary" @click="loadSamples">重新加载</el-button></EmptyState
          >
          <EmptyState
            v-else-if="!samples.length"
            icon="quiz"
            title="这个评测集还没有样本"
            description="对照“资料分段参考”添加问题，并标出能回答问题的原文片段。"
          >
            <el-button type="primary" @click="openSampleDialog()">新增样本</el-button>
          </EmptyState>
          <div v-else>
            <div class="rag-sample-list">
              <article v-for="sample in visibleSamples" :key="sample.id" class="rag-sample-card">
                <div class="rag-sample-card__head">
                  <div>
                    <strong>{{ sample.queryText }}</strong>
                    <span>#{{ sample.id }} · {{ sample.tag || '未分类' }} · 难度 {{ sample.difficulty || 3 }}</span>
                  </div>
                  <div class="workspace-action-row workspace-action-row--fit">
                    <el-button link type="primary" @click="openSampleDialog(sample)">编辑</el-button>
                    <el-button link type="danger" @click="removeSample(sample)">删除</el-button>
                  </div>
                </div>
                <div class="rag-sample-card__meta">
                  <span>标准段落：{{ formatNumberList(sample.expectedSegmentIds) }}</span>
                  <span>标准页码：{{ formatNumberList(sample.expectedPageNos) }}</span>
                  <span>关键词：{{ sample.expectedKeywords || '未填写' }}</span>
                </div>
                <p v-if="sample.note" class="rag-sample-card__note">{{ sample.note }}</p>
              </article>
            </div>

            <div v-if="samples.length > INITIAL_SAMPLE_VISIBLE_COUNT" class="rag-sample-more">
              <span>已显示 {{ visibleSamples.length }} / {{ samples.length }} 条样本</span>
              <div class="rag-sample-more__actions">
                <el-button v-if="hasMoreSamples" type="primary" plain @click="expandSamples">
                  再展开 {{ nextSampleExpandCount }} 条
                </el-button>
                <el-button v-if="sampleVisibleCount > INITIAL_SAMPLE_VISIBLE_COUNT" @click="collapseSamples">
                  收起
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="selectedDataset" class="workspace-panel rag-run-panel">
      <div class="workspace-toolbar">
        <div>
          <h3 class="rag-run-title">运行历史 · {{ selectedDataset.name }}</h3>
          <p class="page-desc">选择 2–3 次运行并列比较。记录保存当时的参数，切换当前配置不会改写历史。</p>
        </div>
        <div class="toolbar toolbar--flush">
          <el-button :loading="runHistoryLoading" @click="loadRunHistory">刷新历史</el-button>
          <el-button
            type="primary"
            :disabled="!selectedDataset.sampleCount || runningEval"
            @click="runDataset(selectedDataset)"
            >运行新方案</el-button
          >
        </div>
      </div>
      <div class="workspace-body">
        <div class="rag-history-actions">
          <span>已选择 {{ selectedRunIds.length }} / 3 次运行</span>
          <el-button
            type="primary"
            plain
            :disabled="selectedRunIds.length < 2"
            :loading="comparisonLoading"
            @click="compareRuns"
            >对比所选运行</el-button
          >
          <el-button :disabled="!selectedRunIds.length" @click="clearComparisonSelection">清除选择</el-button>
        </div>
        <div v-if="runHistoryLoading" class="state-block" role="status">正在加载运行历史…</div>
        <div v-else-if="runHistoryError" class="state-block" role="alert">
          {{ runHistoryError }}<el-button @click="loadRunHistory">重试</el-button>
        </div>
        <div v-else-if="!runHistory.length" class="state-block empty">
          还没有运行记录。选择检索策略后开始第一次评测。
        </div>
        <div v-else class="rag-history-list">
          <article
            v-for="run in runHistory"
            :key="run.id"
            class="rag-history-row"
            :class="{ 'rag-history-row--selected': lastRun?.id === run.id }"
          >
            <input
              type="checkbox"
              :checked="selectedRunIds.includes(run.id)"
              :disabled="selectedRunIds.length >= 3 && !selectedRunIds.includes(run.id)"
              :aria-label="`选择 ${run.name || `运行 #${run.id}`} 参与对比`"
              @change="toggleRunSelection(run.id, ($event.target as HTMLInputElement).checked)"
            />
            <div class="rag-history-row__title">
              <strong>{{ run.name || `运行 #${run.id}` }}</strong
              ><span>#{{ run.id }} · {{ formatDateTime(run.startedAt) }}</span>
            </div>
            <div class="rag-history-row__meta">
              <span>{{ formatRagStrategy(run.strategy) }} · Top K {{ run.retrievalLimit }}</span
              ><small
                >{{ formatRunStatus(run.status) }} · {{ run.evaluatedSamples }}/{{ run.totalSamples }} 样本 · MRR
                {{ formatMetric(run.mrr) }}</small
              ><small v-if="!run.parameterSnapshot">参数未记录</small
              ><small v-else-if="run.mockEmbedding">模拟向量</small
              ><small v-if="run.degradedSamples">{{ run.degradedSamples }} 条样本降级检索</small>
            </div>
            <el-button link type="primary" :loading="runLoading && pendingRunId === run.id" @click="loadRun(run.id)"
              >查看结果</el-button
            >
          </article>
        </div>
        <div v-if="runHistoryTotal" class="workspace-pagination">
          <span>共 {{ runHistoryTotal }} 次运行</span>
          <el-pagination
            v-model:current-page="runHistoryPage.current"
            v-model:page-size="runHistoryPage.size"
            :page-sizes="[10, 20, 50]"
            :total="runHistoryTotal"
            :pager-count="5"
            layout="sizes, prev, pager, next"
            @current-change="loadRunHistory"
            @size-change="resetRunHistoryPage"
          />
        </div>
      </div>
    </div>

    <div v-if="comparisonLoading || comparisonError || comparison" class="workspace-panel rag-run-panel">
      <div class="workspace-toolbar"><h3 class="rag-run-title">方案对比</h3></div>
      <div class="workspace-body">
        <div v-if="comparisonLoading" class="state-block" role="status">正在读取所选运行与参数…</div>
        <div v-else-if="comparisonError" class="state-block" role="alert">
          {{ comparisonError }}<el-button @click="compareRuns">重试</el-button>
        </div>
        <RagRunComparison v-else-if="comparison" :comparison="comparison" />
      </div>
    </div>

    <div v-if="runLoading || runError" class="workspace-panel rag-run-panel">
      <div class="workspace-body state-block" :role="runError ? 'alert' : 'status'">
        {{ runLoading ? '正在加载评测结果…' : runError
        }}<el-button v-if="runError && pendingRunId" @click="loadRun(pendingRunId)">重新加载</el-button>
      </div>
    </div>
    <div v-else-if="lastRun" class="workspace-panel rag-run-panel">
      <div class="workspace-toolbar">
        <div>
          <h3 class="rag-run-title">{{ lastRun.name || '评测结果' }} #{{ lastRun.id }}</h3>
          <p class="page-desc">
            状态 {{ formatRunStatus(lastRun.status) }} · 样本 {{ lastRun.evaluatedSamples }}/{{
              lastRun.totalSamples
            }}
            · 失败 {{ lastRun.failedSamples }}
          </p>
        </div>
        <div class="workspace-toolbar__meta">
          <span class="workspace-chip">Limit {{ lastRun.retrievalLimit }}</span>
          <span class="workspace-chip workspace-chip--brand">MRR {{ formatMetric(lastRun.mrr) }}</span>
        </div>
      </div>

      <div class="workspace-body">
        <RagRunParameters :run="lastRun" />
        <el-alert
          v-if="lastRun.errorMessage"
          class="rag-run-notice"
          type="warning"
          :closable="false"
          show-icon
          title="本次运行需要注意"
          :description="lastRun.errorMessage"
        />
        <div class="rag-metric-grid">
          <div class="summary-overview-card">
            <span>Hit@1 / 3 / 5</span>
            <strong
              >{{ formatMetric(lastRun.hitAt1) }} / {{ formatMetricAtK(lastRun.hitAt3, 3) }} /
              {{ formatMetricAtK(lastRun.hitAt5, 5) }}</strong
            >
          </div>
          <div class="summary-overview-card">
            <span>Recall@1 / 3 / 5</span>
            <strong
              >{{ formatMetric(lastRun.recallAt1) }} / {{ formatMetricAtK(lastRun.recallAt3, 3) }} /
              {{ formatMetricAtK(lastRun.recallAt5, 5) }}</strong
            >
          </div>
          <div class="summary-overview-card">
            <span>平均耗时</span>
            <strong>{{ formatLatency(lastRun.avgLatencyMs) }}</strong>
          </div>
        </div>

        <p v-if="lastRun.retrievalLimit < 5" class="page-desc">
          — 表示本次 Top K 未覆盖该指标，不能视为已完成该 K 值的评测。
        </p>

        <div class="workspace-table rag-run-table">
          <div class="workspace-table__head workspace-table__head--rag-run">
            <span>问题</span>
            <span>标准</span>
            <span>实际召回</span>
            <span>命中排名</span>
            <span>指标</span>
          </div>
          <div
            v-for="item in visibleRunItems"
            :key="item.id"
            class="workspace-table__row workspace-table__row--rag-run"
          >
            <div class="workspace-table__title">
              <strong>{{ item.queryText }}</strong>
              <span>样本 #{{ item.sampleId }} · {{ item.latencyMs || 0 }}ms</span>
            </div>
            <div class="task-meta-stack" data-label="标准">
              <span>段落 {{ formatNumberList(item.expectedSegmentIds) }}</span>
              <span>页码 {{ formatNumberList(item.expectedPageNos) }}</span>
            </div>
            <div class="task-meta-stack" data-label="实际召回">
              <span>段落 {{ formatNumberList(item.retrievedSegmentIds) }}</span>
              <span>页码 {{ formatNumberList(item.retrievedPageNos) }}</span>
              <RagRunEvidence :item="item" />
            </div>
            <strong data-label="命中排名" :class="item.hitRank ? 'rag-hit-rank--hit' : 'rag-hit-rank--miss'">
              {{ item.hitRank ? `第 ${item.hitRank} 名` : '未命中' }}
            </strong>
            <div class="task-meta-stack" data-label="指标">
              <span>R@1 {{ formatMetric(item.recallAt1) }}</span>
              <span>R@3 {{ formatMetricAtK(item.recallAt3, 3) }} · R@5 {{ formatMetricAtK(item.recallAt5, 5) }}</span>
              <span v-if="item.errorMessage" class="rag-error-text">{{ item.errorMessage }}</span>
            </div>
          </div>
          <div v-if="lastRun.items.length > visibleRunItems.length" class="rag-run-more">
            <span>已显示 {{ visibleRunItems.length }} / {{ lastRun.items.length }} 条</span>
            <el-button @click="runVisibleCount += RUN_VISIBLE_STEP">显示更多</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="datasetDialogVisible" title="新建 RAG 评测集" width="640px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="关联资料">
          <el-select
            v-model="datasetForm.materialId"
            filterable
            style="width: 100%"
            placeholder="请选择已完成 Embedding 的资料"
            :loading="materialsLoading"
          >
            <el-option
              v-for="item in materials"
              :key="item.id"
              :label="`${item.title} · ${formatEmbeddingProgress(item)} · #${item.id}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="评测集名称">
          <el-input
            v-model="datasetForm.name"
            maxlength="120"
            show-word-limit
            placeholder="例如：计算机网络 · 第一轮检索评测"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="datasetForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明评测范围、标注原则或版本"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="datasetDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creatingDataset" @click="createDataset">创建</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="cmrcDialogVisible" title="导入 CMRC2018 公开评测集" width="720px" destroy-on-close>
      <el-alert
        title="支持 CMRC2018 原始 JSON，以及包含 context/question/answers 字段的 JSON 或 JSONL。导入后会自动创建资料、分段、评测集和样本。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-position="top">
        <el-form-item label="数据文件">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :show-file-list="true"
            accept=".json,.jsonl,.txt"
            :on-change="handleCmrcFileChange"
            :on-remove="clearCmrcFile"
          >
            <div class="cmrc-upload-box">
              <strong>{{ cmrcFile?.name || '点击或拖拽 CMRC2018 JSON 文件到这里' }}</strong>
              <span>建议先用 dev/trial 小集，确认流程后再导入更大的 split。</span>
            </div>
          </el-upload>
        </el-form-item>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="Split 名称">
            <el-input v-model="cmrcForm.splitName" placeholder="dev / trial / train" />
          </el-form-item>
          <el-form-item label="最大样本数">
            <el-input-number v-model="cmrcForm.maxSamples" :min="1" :max="5000" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="资料名称">
          <el-input v-model="cmrcForm.materialTitle" maxlength="120" placeholder="留空则自动生成" />
        </el-form-item>
        <el-form-item label="评测集名称">
          <el-input v-model="cmrcForm.datasetName" maxlength="120" placeholder="留空则自动生成" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="cmrcForm.submitEmbeddingTask"> 导入后自动提交 Embedding 任务 </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="cmrcDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="importingCmrc" @click="importCmrcDataset">开始导入</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="sampleDialogVisible"
      :title="editingSampleId ? '编辑评测样本' : '新增评测样本'"
      width="760px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="检索问题">
          <el-input
            v-model="sampleForm.queryText"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：细胞膜的主要成分是什么？"
          />
        </el-form-item>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="标准相关段落 ID">
            <el-input v-model="sampleForm.expectedSegmentIdsText" placeholder="例如：12,13,18" />
          </el-form-item>
          <el-form-item label="标准相关页码">
            <el-input v-model="sampleForm.expectedPageNosText" placeholder="例如：18,19" />
          </el-form-item>
        </div>
        <div class="workspace-form-grid workspace-form-grid--compact">
          <el-form-item label="标签">
            <el-input v-model="sampleForm.tag" placeholder="例如：概念 / 实验 / 流程 / 区别" />
          </el-form-item>
          <el-form-item label="难度">
            <el-slider v-model="sampleForm.difficulty" :min="1" :max="5" show-stops />
          </el-form-item>
        </div>
        <el-form-item label="期望关键词">
          <el-input
            v-model="sampleForm.expectedKeywords"
            maxlength="500"
            placeholder="例如：磷脂双分子层、蛋白质、糖类"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="sampleForm.note"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="可记录标注依据、容易混淆的位置等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: space-between">
          <el-button :disabled="!selectedDataset" @click="openSegmentDrawer">查看分段 ID</el-button>
          <div>
            <el-button @click="sampleDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingSample" @click="saveSample">保存样本</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialogVisible" title="批量导入评测样本" width="820px" destroy-on-close>
      <el-alert
        title="粘贴 JSON 数组即可批量导入。至少填写 queryText，并填写 expectedSegmentIds 或 expectedPageNos 其中一个。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
      />
      <el-input v-model="batchJsonText" type="textarea" :rows="14" :placeholder="batchPlaceholder" />
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button @click="batchDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingSample" @click="importBatchSamples">导入样本</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="runDialogVisible"
      title="运行新方案"
      width="min(580px, calc(100vw - 32px))"
      :close-on-click-modal="!runningEval"
      :close-on-press-escape="!runningEval"
      :show-close="!runningEval"
      destroy-on-close
    >
      <el-form label-position="top">
        <p class="page-desc">评测集：{{ pendingRunDataset?.name }}</p>
        <el-form-item label="方案名称（选填）"
          ><el-input
            v-model="runForm.name"
            maxlength="80"
            show-word-limit
            :disabled="runningEval"
            placeholder="例如：纯向量基线 / 调整分块后"
        /></el-form-item>
        <el-form-item label="检索策略"
          ><el-select v-model="runForm.strategy" :disabled="runningEval" style="width: 100%"
            ><el-option
              v-for="(label, value) in RAG_STRATEGY_LABELS"
              :key="value"
              :label="label"
              :value="value" /></el-select
        ></el-form-item>
        <el-form-item label="检索返回条数 Top K">
          <el-input-number v-model="runForm.limit" :min="1" :max="20" :disabled="runningEval" style="width: 100%" />
        </el-form-item>
      </el-form>
      <el-alert
        :title="
          runForm.strategy === 'TEXT'
            ? '文本检索直接匹配已解析资料，不需要向量服务。'
            : '向量方案需要可用的资料索引；实际降级和模拟向量情况会保存在运行记录中。'
        "
        type="info"
        :closable="false"
        show-icon
      />
      <p class="page-desc">分块使用资料当前已解析的版本。修改大小或重叠需先到资料库重新解析，历史运行会保留原参数。</p>
      <template #footer>
        <div class="toolbar" style="margin-bottom: 0; justify-content: flex-end">
          <el-button :disabled="runningEval" @click="runDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="runningEval" @click="confirmRunDataset">开始评测</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="segmentDrawerVisible" title="资料分段参考" size="58%">
      <div v-if="segmentLoading" class="state-block" role="status">正在加载资料分段…</div>
      <template v-else-if="materialDetail">
        <div class="detail-meta-grid" style="margin-bottom: 18px">
          <div class="detail-meta-item">
            <span>资料</span>
            <strong>{{ materialDetail.title }}</strong>
          </div>
          <div class="detail-meta-item">
            <span>分段数</span>
            <strong>{{ materialDetail.segments?.length || 0 }}</strong>
          </div>
        </div>
        <div class="rag-segment-list">
          <article v-for="segment in materialDetail.segments || []" :key="segment.id" class="rag-segment-card">
            <div class="rag-segment-card__head">
              <strong>#{{ segment.id }} · {{ segment.sectionTitle || '未命名片段' }}</strong>
              <span>{{ formatSegmentLocation(segment) }}</span>
            </div>
            <p>{{ segment.contentText }}</p>
          </article>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { getMaterialDetailApi, getMaterialPageApi, type MaterialPageItem } from '@/api/modules/material'
import {
  addRagEvalSamplesApi,
  createRagEvalDatasetApi,
  deleteRagEvalDatasetApi,
  deleteRagEvalSampleApi,
  getRagEvalDatasetPageApi,
  getRagEvalRunApi,
  getRagEvalRunsApi,
  compareRagEvalRunsApi,
  formatRagStrategy,
  RAG_STRATEGY_LABELS,
  getRagEvalSamplesApi,
  importCmrc2018Api,
  runRagEvalDatasetApi,
  updateRagEvalSampleApi,
  type CmrcImportResult,
  type RagEvalDataset,
  type RagEvalDatasetPagePayload,
  type RagEvalRun,
  type RagEvalRunPagePayload,
  type RagEvalComparison,
  type RagRetrievalStrategy,
  type RagEvalSample,
  type RagEvalSampleForm
} from '@/api/modules/ragEval'
import { useAutoListQuery } from '@/composables/useAutoListQuery'
import { getErrorMessage } from '@/utils/errorMessage'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { formatDateTime, formatSegmentMeta } from '@/utils/format'
import RagRunParameters from '@/components/rag/RagRunParameters.vue'
import RagRunComparison from '@/components/rag/RagRunComparison.vue'
import RagRunEvidence from '@/components/rag/RagRunEvidence.vue'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

interface MaterialSegment {
  id: number
  segmentNo?: number
  pageNo?: number
  sectionTitle?: string
  contentText?: string
}

interface MaterialDetail {
  id: number
  title: string
  segments?: MaterialSegment[]
}

const datasets = ref<RagEvalDataset[]>([])
const materials = ref<MaterialPageItem[]>([])
const samples = ref<RagEvalSample[]>([])
const selectedDataset = ref<RagEvalDataset | null>(null)
const lastRun = ref<RagEvalRun | null>(null)
const materialDetail = ref<MaterialDetail | null>(null)
const runHistory = ref<RagEvalRun[]>([])
const runHistoryTotal = ref(0)
const runHistoryLoading = ref(false)
const runHistoryError = ref('')
const runHistoryPage = reactive({ current: 1, size: 10 })
const selectedRunIds = ref<number[]>([])
const comparison = ref<RagEvalComparison | null>(null)
const comparisonLoading = ref(false)
const comparisonError = ref('')
const runLoading = ref(false)
const runError = ref('')
const pendingRunId = ref<number | null>(null)
let viewDisposed = false

const datasetLoading = ref(false)
const datasetError = ref('')
const sampleError = ref('')
const materialsLoading = ref(false)
const sampleLoading = ref(false)
const segmentLoading = ref(false)
const creatingDataset = ref(false)
const savingSample = ref(false)
const runningEval = ref(false)
const datasetDialogVisible = ref(false)
const sampleDialogVisible = ref(false)
const batchDialogVisible = ref(false)
const runDialogVisible = ref(false)
const segmentDrawerVisible = ref(false)
const cmrcDialogVisible = ref(false)
const editingSampleId = ref<number | null>(null)
const pendingRunDataset = ref<RagEvalDataset | null>(null)
const batchJsonText = ref('')
const cmrcFile = ref<File | null>(null)
const datasetTotal = ref(0)
const importingCmrc = ref(false)

const INITIAL_SAMPLE_VISIBLE_COUNT = 6
const SAMPLE_VISIBLE_STEP = 6
const sampleVisibleCount = ref(INITIAL_SAMPLE_VISIBLE_COUNT)
const RUN_VISIBLE_STEP = 50
const runVisibleCount = ref(RUN_VISIBLE_STEP)

const filters = reactive({
  keyword: '',
  materialId: undefined as number | undefined
})
const hasFilters = computed(() => Boolean(filters.keyword || filters.materialId))

const datasetPage = reactive({
  current: 1,
  size: 10
})

const autoQuery = useAutoListQuery([() => filters.keyword, () => filters.materialId], () => {
  datasetPage.current = 1
  return loadDatasets()
})

const datasetForm = reactive({
  materialId: undefined as number | undefined,
  name: '',
  description: ''
})

const sampleForm = reactive({
  queryText: '',
  expectedSegmentIdsText: '',
  expectedPageNosText: '',
  expectedKeywords: '',
  tag: '',
  difficulty: 3,
  note: ''
})

const runForm = reactive({
  limit: 5,
  strategy: 'VECTOR_RERANK' as RagRetrievalStrategy,
  name: ''
})

const cmrcForm = reactive({
  splitName: 'dev',
  maxSamples: 500,
  materialTitle: '',
  datasetName: '',
  submitEmbeddingTask: false
})

const batchPlaceholder = `[
  {
    "queryText": "细胞膜的主要成分是什么？",
    "expectedSegmentIds": [12, 13],
    "expectedPageNos": [18],
    "tag": "概念",
    "difficulty": 2,
    "expectedKeywords": "脂质,蛋白质"
  }
]`

const selectedMaterial = computed(() => materials.value.find((item) => item.id === datasetForm.materialId))

const visibleRunItems = computed(() => (lastRun.value?.items || []).slice(0, runVisibleCount.value))
watch(
  () => lastRun.value?.id,
  () => {
    runVisibleCount.value = RUN_VISIBLE_STEP
  }
)

const visibleSamples = computed(() => samples.value.slice(0, sampleVisibleCount.value))

const hasMoreSamples = computed(() => sampleVisibleCount.value < samples.value.length)

const nextSampleExpandCount = computed(() =>
  Math.min(SAMPLE_VISIBLE_STEP, Math.max(0, samples.value.length - sampleVisibleCount.value))
)

const resetSampleVisibleCount = () => {
  sampleVisibleCount.value = INITIAL_SAMPLE_VISIBLE_COUNT
}

const expandSamples = () => {
  sampleVisibleCount.value = Math.min(samples.value.length, sampleVisibleCount.value + SAMPLE_VISIBLE_STEP)
}

const collapseSamples = () => {
  resetSampleVisibleCount()
}

const formatMetric = (value?: number) => {
  if (typeof value !== 'number') {
    return '--'
  }
  return `${(value * 100).toFixed(1)}%`
}
const formatMetricAtK = (value: number | undefined, k: number) =>
  (lastRun.value?.retrievalLimit || 0) < k ? '—' : formatMetric(value)

const formatLatency = (value?: number) => {
  if (typeof value !== 'number') {
    return '--'
  }
  return `${Math.round(value)} ms`
}

const formatRunStatus = (value?: string) => {
  switch ((value || '').toUpperCase()) {
    case 'SUCCESS':
      return '成功'
    case 'PARTIAL_FAILED':
      return '部分失败'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '运行中'
    default:
      return value || '未知'
  }
}

const formatNumberList = (values?: number[]) => {
  if (!values || !values.length) {
    return '--'
  }
  return values.join(', ')
}

const formatSegmentLocation = (segment: { pageNo?: number; segmentNo?: number }) => formatSegmentMeta(segment)

const formatEmbeddingProgress = (row: MaterialPageItem) => {
  const embedded = Number(row.embeddedSegmentCount || 0)
  const total = Number(row.totalSegmentCount || 0)
  if (total > 0) {
    return `Embedding ${embedded}/${total}`
  }
  return row.parseStatus || '未解析'
}

const parseNumberList = (text: string) => {
  if (!text.trim()) {
    return []
  }
  const values = text
    .split(/[,，\s]+/)
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0)
  return Array.from(new Set(values))
}

const buildSamplePayload = (): RagEvalSampleForm | null => {
  const queryText = sampleForm.queryText.trim()
  const expectedSegmentIds = parseNumberList(sampleForm.expectedSegmentIdsText)
  const expectedPageNos = parseNumberList(sampleForm.expectedPageNosText)
  if (!queryText) {
    ElMessage.warning('请填写检索问题')
    return null
  }
  if (!expectedSegmentIds.length && !expectedPageNos.length) {
    ElMessage.warning('请至少填写标准相关段落 ID 或标准相关页码')
    return null
  }
  return {
    queryText,
    expectedSegmentIds,
    expectedPageNos,
    expectedKeywords: sampleForm.expectedKeywords.trim() || undefined,
    tag: sampleForm.tag.trim() || undefined,
    difficulty: sampleForm.difficulty,
    sourceType: 'HUMAN',
    note: sampleForm.note.trim() || undefined
  }
}

const loadMaterials = async () => {
  materialsLoading.value = true
  try {
    const res = await getMaterialPageApi({
      current: 1,
      size: 50,
      parseStatus: 'SUCCESS'
    })
    materials.value = res.data.data.records || []
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '加载资料列表失败'))
  } finally {
    materialsLoading.value = false
  }
}

const datasetRequest = useLatestRequest()
const sampleRequest = useLatestRequest()
const runHistoryRequest = useLatestRequest()
const runRequest = useLatestRequest()
const comparisonRequest = useLatestRequest()
const segmentRequest = useLatestRequest()

const clearComparisonSelection = () => {
  selectedRunIds.value = []
  comparison.value = null
  comparisonError.value = ''
  comparisonLoading.value = false
  comparisonRequest.invalidate()
}

const resetSelectedDataset = (dataset: RagEvalDataset | null) => {
  selectedDataset.value = dataset
  samples.value = []
  sampleError.value = ''
  lastRun.value = null
  materialDetail.value = null
  segmentDrawerVisible.value = false
  runHistory.value = []
  runHistoryTotal.value = 0
  runHistoryPage.current = 1
  runHistoryError.value = ''
  runError.value = ''
  pendingRunId.value = null
  runLoading.value = false
  sampleLoading.value = false
  runHistoryLoading.value = false
  sampleRequest.invalidate()
  runHistoryRequest.invalidate()
  runRequest.invalidate()
  segmentRequest.invalidate()
  clearComparisonSelection()
}

const loadRunHistory = async () => {
  const datasetId = selectedDataset.value?.id
  if (!datasetId) return
  const requestId = runHistoryRequest.begin()
  runHistoryLoading.value = true
  runHistoryError.value = ''
  try {
    const res = await getRagEvalRunsApi(datasetId, { ...runHistoryPage })
    if (viewDisposed || !runHistoryRequest.isLatest(requestId) || selectedDataset.value?.id !== datasetId) return
    const data = res.data.data as RagEvalRunPagePayload
    runHistory.value = data.records || []
    runHistoryTotal.value = data.total || 0
  } catch (error) {
    if (!viewDisposed && runHistoryRequest.isLatest(requestId))
      runHistoryError.value = getErrorMessage(error, '加载运行历史失败')
  } finally {
    if (runHistoryRequest.isLatest(requestId)) runHistoryLoading.value = false
  }
}

const resetRunHistoryPage = () => {
  runHistoryPage.current = 1
  void loadRunHistory()
}

const toggleRunSelection = (runId: number, checked: boolean) => {
  if (checked && selectedRunIds.value.length < 3 && !selectedRunIds.value.includes(runId))
    selectedRunIds.value = [...selectedRunIds.value, runId]
  if (!checked) selectedRunIds.value = selectedRunIds.value.filter((id) => id !== runId)
  comparisonRequest.invalidate()
  comparison.value = null
  comparisonError.value = ''
  comparisonLoading.value = false
}

const compareRuns = async () => {
  const datasetId = selectedDataset.value?.id
  if (!datasetId || selectedRunIds.value.length < 2 || selectedRunIds.value.length > 3) return
  const requestId = comparisonRequest.begin()
  comparisonLoading.value = true
  comparisonError.value = ''
  comparison.value = null
  try {
    const res = await compareRagEvalRunsApi(datasetId, [...selectedRunIds.value])
    if (viewDisposed || !comparisonRequest.isLatest(requestId) || selectedDataset.value?.id !== datasetId) return
    comparison.value = res.data.data as RagEvalComparison
  } catch (error) {
    if (!viewDisposed && comparisonRequest.isLatest(requestId))
      comparisonError.value = getErrorMessage(error, '读取对比结果失败')
  } finally {
    if (comparisonRequest.isLatest(requestId)) comparisonLoading.value = false
  }
}

const loadDatasets = async () => {
  const requestId = datasetRequest.begin()
  datasetLoading.value = true
  datasetError.value = ''
  try {
    const res = await getRagEvalDatasetPageApi({
      current: datasetPage.current,
      size: datasetPage.size,
      keyword: filters.keyword || undefined,
      materialId: filters.materialId || undefined
    })
    if (!datasetRequest.isLatest(requestId)) return
    const data = res.data.data as RagEvalDatasetPagePayload
    datasets.value = data.records || []
    datasetTotal.value = data.total || 0
    if (selectedDataset.value) {
      const freshSelected = datasets.value.find((item) => item.id === selectedDataset.value?.id)
      if (freshSelected) {
        selectedDataset.value = freshSelected
      }
    }
  } catch (error) {
    if (!datasetRequest.isLatest(requestId)) return
    datasetError.value = getErrorMessage(error, '暂时无法读取评测集，请稍后重试。')
  } finally {
    if (datasetRequest.isLatest(requestId)) datasetLoading.value = false
  }
}

const resetFilters = () => {
  autoQuery.runAfterMutation(() => {
    filters.keyword = ''
    filters.materialId = undefined
    datasetPage.current = 1
  })
}

const openDatasetDialog = () => {
  datasetForm.materialId = undefined
  datasetForm.name = ''
  datasetForm.description = ''
  datasetDialogVisible.value = true
  if (!materials.value.length) {
    void loadMaterials()
  }
}

const openCmrcDialog = () => {
  cmrcFile.value = null
  cmrcForm.splitName = 'dev'
  cmrcForm.maxSamples = 500
  cmrcForm.materialTitle = ''
  cmrcForm.datasetName = ''
  cmrcForm.submitEmbeddingTask = false
  cmrcDialogVisible.value = true
}

const handleCmrcFileChange = (uploadFile: UploadFile) => {
  cmrcFile.value = uploadFile.raw || null
}

const clearCmrcFile = () => {
  cmrcFile.value = null
}

const importCmrcDataset = async () => {
  if (!cmrcFile.value) {
    ElMessage.warning('请先选择 CMRC2018 数据文件')
    return
  }
  importingCmrc.value = true
  try {
    const res = await importCmrc2018Api({
      file: cmrcFile.value,
      materialTitle: cmrcForm.materialTitle.trim() || undefined,
      datasetName: cmrcForm.datasetName.trim() || undefined,
      splitName: cmrcForm.splitName.trim() || undefined,
      maxSamples: cmrcForm.maxSamples,
      submitEmbeddingTask: cmrcForm.submitEmbeddingTask
    })
    const result = res.data.data as CmrcImportResult
    ElMessage.success(
      `已导入 ${result.sampleCount} 条样本、${result.segmentCount} 个分段${result.embeddingTaskId ? `，Embedding 任务 #${result.embeddingTaskId}` : ''}`
    )
    cmrcDialogVisible.value = false
    datasetPage.current = 1
    await loadMaterials()
    await loadDatasets()
    resetSelectedDataset({
      id: result.datasetId,
      materialId: result.materialId,
      materialTitle: result.materialTitle,
      name: result.datasetName,
      status: 'ACTIVE',
      sampleCount: result.sampleCount
    })
    await Promise.all([loadSamples(), loadRunHistory()])
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '导入 CMRC2018 失败'))
  } finally {
    importingCmrc.value = false
  }
}

const createDataset = async () => {
  if (!datasetForm.materialId) {
    ElMessage.warning('请选择关联资料')
    return
  }
  if (!datasetForm.name.trim()) {
    ElMessage.warning('请填写评测集名称')
    return
  }

  creatingDataset.value = true
  try {
    const res = await createRagEvalDatasetApi({
      materialId: datasetForm.materialId,
      name: datasetForm.name.trim(),
      description: datasetForm.description.trim() || undefined
    })
    const dataset = res.data.data as RagEvalDataset
    ElMessage.success('评测集已创建')
    datasetDialogVisible.value = false
    resetSelectedDataset({
      ...dataset,
      materialTitle: dataset.materialTitle || selectedMaterial.value?.title
    })
    await loadDatasets()
    await Promise.all([loadSamples(), loadRunHistory()])
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '创建评测集失败'))
  } finally {
    creatingDataset.value = false
  }
}

const selectDataset = async (dataset: RagEvalDataset) => {
  if (selectedDataset.value?.id !== dataset.id) resetSelectedDataset(dataset)
  await Promise.all([loadSamples(), loadRunHistory()])
}

const removeDataset = async (dataset: RagEvalDataset) => {
  try {
    await ElMessageBox.confirm('删除后会同时删除该评测集的样本和历史运行记录，确定继续吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteRagEvalDatasetApi(dataset.id)
    ElMessage.success('评测集已删除')
    if (selectedDataset.value?.id === dataset.id) {
      resetSelectedDataset(null)
    }
    await loadDatasets()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除评测集失败'))
  }
}

const loadSamples = async () => {
  if (!selectedDataset.value) {
    return
  }
  const datasetId = selectedDataset.value.id
  const requestId = sampleRequest.begin()
  sampleLoading.value = true
  sampleError.value = ''
  try {
    const res = await getRagEvalSamplesApi(datasetId)
    if (!sampleRequest.isLatest(requestId) || selectedDataset.value?.id !== datasetId) return
    samples.value = res.data.data || []
    resetSampleVisibleCount()
  } catch (error) {
    if (!sampleRequest.isLatest(requestId)) return
    sampleError.value = getErrorMessage(error, '暂时无法读取评测样本，请稍后重试。')
  } finally {
    if (sampleRequest.isLatest(requestId)) sampleLoading.value = false
  }
}

const resetSampleForm = () => {
  editingSampleId.value = null
  sampleForm.queryText = ''
  sampleForm.expectedSegmentIdsText = ''
  sampleForm.expectedPageNosText = ''
  sampleForm.expectedKeywords = ''
  sampleForm.tag = ''
  sampleForm.difficulty = 3
  sampleForm.note = ''
}

const openSampleDialog = (sample?: RagEvalSample) => {
  if (!selectedDataset.value) {
    return
  }
  resetSampleForm()
  if (sample) {
    editingSampleId.value = sample.id
    sampleForm.queryText = sample.queryText
    sampleForm.expectedSegmentIdsText = formatNumberList(sample.expectedSegmentIds)
    sampleForm.expectedPageNosText = formatNumberList(sample.expectedPageNos)
    sampleForm.expectedKeywords = sample.expectedKeywords || ''
    sampleForm.tag = sample.tag || ''
    sampleForm.difficulty = sample.difficulty || 3
    sampleForm.note = sample.note || ''
  }
  sampleDialogVisible.value = true
}

const saveSample = async () => {
  if (!selectedDataset.value) {
    return
  }
  const payload = buildSamplePayload()
  if (!payload) {
    return
  }

  savingSample.value = true
  try {
    if (editingSampleId.value) {
      await updateRagEvalSampleApi(selectedDataset.value.id, editingSampleId.value, payload)
      ElMessage.success('评测样本已更新')
    } else {
      await addRagEvalSamplesApi(selectedDataset.value.id, [payload])
      ElMessage.success('评测样本已添加')
    }
    sampleDialogVisible.value = false
    await loadSamples()
    await loadDatasets()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '保存评测样本失败'))
  } finally {
    savingSample.value = false
  }
}

const removeSample = async (sample: RagEvalSample) => {
  if (!selectedDataset.value) {
    return
  }
  try {
    await ElMessageBox.confirm('删除后该样本不再参与新评测，已有运行结果和当时的标注会保留。确定删除吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteRagEvalSampleApi(selectedDataset.value.id, sample.id)
    ElMessage.success('评测样本已删除')
    await loadSamples()
    await loadDatasets()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '删除评测样本失败'))
  }
}

const openBatchDialog = () => {
  batchJsonText.value = batchPlaceholder
  batchDialogVisible.value = true
}

const importBatchSamples = async () => {
  if (!selectedDataset.value) {
    return
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(batchJsonText.value)
  } catch {
    ElMessage.error('JSON 格式不正确')
    return
  }
  if (!Array.isArray(parsed)) {
    ElMessage.error('请粘贴 JSON 数组')
    return
  }
  const samplesToImport = parsed.map((item) => item as RagEvalSampleForm)
  if (!samplesToImport.length) {
    ElMessage.warning('没有可导入的样本')
    return
  }

  savingSample.value = true
  try {
    await addRagEvalSamplesApi(selectedDataset.value.id, samplesToImport)
    ElMessage.success(`已导入 ${samplesToImport.length} 条样本`)
    batchDialogVisible.value = false
    await loadSamples()
    await loadDatasets()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '批量导入失败'))
  } finally {
    savingSample.value = false
  }
}

const runDataset = (dataset: RagEvalDataset) => {
  if (runningEval.value) return
  if (selectedDataset.value?.id !== dataset.id) void selectDataset(dataset)
  pendingRunDataset.value = dataset
  runForm.limit = 5
  runForm.strategy = 'VECTOR_RERANK'
  runForm.name = ''
  runDialogVisible.value = true
}

const confirmRunDataset = async () => {
  if (!pendingRunDataset.value || runningEval.value) {
    return
  }
  runningEval.value = true
  const dataset = pendingRunDataset.value
  const data = { limit: runForm.limit, strategy: runForm.strategy, name: runForm.name.trim() || undefined }
  try {
    const res = await runRagEvalDatasetApi(dataset.id, data)
    if (viewDisposed) return
    const result = res.data.data as RagEvalRun
    runDialogVisible.value = false
    ElMessage.success(`评测运行已结束：${formatRunStatus(result.status)}`)
    await loadDatasets()
    if (selectedDataset.value?.id === dataset.id) {
      runRequest.invalidate()
      lastRun.value = result
      runLoading.value = false
      runError.value = ''
      pendingRunId.value = result.id
      runHistoryPage.current = 1
      await loadRunHistory()
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '运行评测失败'))
  } finally {
    runningEval.value = false
  }
}

const loadRun = async (runId: number) => {
  const datasetId = selectedDataset.value?.id
  if (!datasetId) return
  const requestId = runRequest.begin()
  pendingRunId.value = runId
  runLoading.value = true
  runError.value = ''
  lastRun.value = null
  try {
    const res = await getRagEvalRunApi(runId)
    if (viewDisposed || !runRequest.isLatest(requestId) || selectedDataset.value?.id !== datasetId) return
    const result = res.data.data as RagEvalRun
    if (result.datasetId !== datasetId) throw new Error('这次运行不属于当前评测集')
    lastRun.value = result
  } catch (error) {
    if (!viewDisposed && runRequest.isLatest(requestId)) runError.value = getErrorMessage(error, '加载评测结果失败')
  } finally {
    if (runRequest.isLatest(requestId)) runLoading.value = false
  }
}

const openSegmentDrawer = async () => {
  if (!selectedDataset.value) {
    return
  }
  segmentDrawerVisible.value = true
  const datasetId = selectedDataset.value.id
  const materialId = selectedDataset.value.materialId
  const requestId = segmentRequest.begin()
  segmentLoading.value = true
  try {
    const res = await getMaterialDetailApi(materialId)
    if (viewDisposed || !segmentRequest.isLatest(requestId) || selectedDataset.value?.id !== datasetId) return
    materialDetail.value = res.data.data as MaterialDetail
  } catch (error) {
    if (!segmentRequest.isLatest(requestId)) return
    materialDetail.value = null
    ElMessage.error(getErrorMessage(error, '加载资料分段失败'))
  } finally {
    if (segmentRequest.isLatest(requestId)) segmentLoading.value = false
  }
}

void loadMaterials()
void loadDatasets()

onUnmounted(() => {
  viewDisposed = true
  datasetRequest.invalidate()
  sampleRequest.invalidate()
  runHistoryRequest.invalidate()
  runRequest.invalidate()
  comparisonRequest.invalidate()
  segmentRequest.invalidate()
})
</script>

<style scoped>
.rag-eval-layout {
  padding: 0;
  gap: 0;
}
.rag-eval-layout > .workspace-card {
  min-width: 0;
  padding: 20px;
  border: 0;
  border-radius: 0;
}
.rag-eval-layout > .workspace-card + .workspace-card {
  border-top: 1px solid var(--line);
}
.rag-section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0;
  color: var(--text);
  font-size: 17px;
  font-weight: 600;
}
.rag-section-title span {
  color: var(--muted);
  font-size: 13px;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}
.rag-eval-view .workspace-pagination {
  padding-inline: 0;
}
.rag-eval-view .workspace-table__row > *,
.rag-eval-view .workspace-table__title strong {
  min-width: 0;
  overflow-wrap: anywhere;
}
.rag-eval-view .workspace-action-row {
  gap: 8px 14px;
}
.rag-eval-view .workspace-action-row .el-button {
  margin: 0;
}
.rag-eval-view .rag-sample-card:hover,
.rag-eval-view .rag-segment-card:hover {
  box-shadow: none;
  background: var(--panel-hover);
}
.rag-history-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}
.rag-history-actions > span {
  font-size: 13px;
  color: var(--muted);
}
.rag-history-row {
  display: grid;
  grid-template-columns: 22px minmax(180px, 1fr) minmax(220px, 1.4fr) auto;
  gap: 16px;
  align-items: center;
  padding: 18px 8px;
  border-bottom: 1px solid var(--line);
}
.rag-history-row--selected {
  background: var(--brand-light);
}
.rag-history-row input {
  width: 17px;
  height: 17px;
  accent-color: var(--brand);
  cursor: pointer;
}
.rag-history-row__title,
.rag-history-row__meta {
  display: grid;
  min-width: 0;
  gap: 5px;
  overflow-wrap: anywhere;
  font-size: 13px;
}
.rag-history-row__title > span,
.rag-history-row__meta small {
  color: var(--muted);
  font-size: 12px;
}
@media (max-width: 720px) {
  .rag-eval-layout > .workspace-card {
    padding: 16px;
  }
  .rag-eval-side .toolbar {
    width: 100%;
  }
  .rag-eval-side .toolbar .el-button {
    flex: 1 1 auto;
  }
  .rag-history-row {
    grid-template-columns: 22px minmax(0, 1fr) auto;
    gap: 10px;
  }
  .rag-history-row__meta {
    grid-column: 2 / -1;
    grid-row: 2;
  }
}
@media (max-width: 760px) {
  .rag-eval-view > .workspace-panel > .workspace-toolbar {
    padding: 0 0 20px;
    border: 0;
  }
  .rag-eval-view > .workspace-panel > .workspace-toolbar > .el-button {
    min-height: 44px;
    padding-inline: 8px;
    font-size: 13px;
  }
  .rag-eval-layout > .workspace-card {
    padding: 24px 0;
    background: transparent;
  }
  .rag-section-title,
  .rag-run-title {
    font-size: 20px;
    line-height: 1.5;
    letter-spacing: -0.02em;
  }
  .rag-eval-view .workspace-table__row {
    padding: 24px 0;
    gap: 14px;
  }
  .rag-eval-view .workspace-table__title strong {
    font-size: 18px;
    line-height: 1.6;
  }
  .rag-eval-view .workspace-action-row {
    gap: 4px 20px;
  }
  .rag-eval-view .workspace-action-row[data-label]::before {
    display: none;
  }
  .rag-eval-view .workspace-action-row .el-button {
    min-height: 44px;
    font-size: 14px;
  }
  .rag-eval-side .toolbar {
    width: 100%;
    gap: 8px;
  }
  .rag-eval-side .toolbar .el-button {
    flex: 1 1 auto;
    margin: 0;
  }
  .rag-eval-view .rag-sample-list {
    gap: 0;
  }
  .rag-eval-view .rag-sample-card {
    padding: 24px 0;
    border: 0;
    border-bottom: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
  }
  .rag-eval-view .rag-sample-card:last-child {
    border-bottom: 0;
  }
  .rag-eval-view .rag-sample-card__head strong {
    font-size: 17px;
  }
  .rag-eval-view .rag-run-panel {
    margin-top: 24px;
    padding-top: 24px;
    border-top: 1px solid var(--line);
  }
  .rag-eval-view .rag-run-panel > .workspace-body {
    padding: 0;
  }
  .rag-metric-grid {
    gap: 0;
    padding: 8px 0 24px;
    border-bottom: 1px solid var(--line);
  }
  .summary-overview-card {
    padding: 18px 0;
    border: 0;
    border-radius: 0;
    background: transparent;
  }
  .summary-overview-card strong {
    font-size: 36px;
    font-weight: 500;
  }
  .rag-history-row {
    grid-template-columns: 22px minmax(0, 1fr) auto;
    gap: 10px;
    padding: 22px 0;
  }
  .rag-history-row__title {
    font-size: 15px;
  }
  .rag-history-row__meta {
    grid-column: 2 / -1;
    grid-row: 2;
  }
  .rag-history-row > .el-button {
    min-height: 44px;
  }
}
</style>
