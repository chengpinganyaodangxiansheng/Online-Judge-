import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import Answer from './Answer.vue'
import { userSubmitService } from '@/apis/user'
import { getQuestionDetailService } from '@/apis/question'

// 隔离路由、编辑器与网络，只验证组件的请求身份、代码快照和重复点击处理。
vi.mock('@/apis/user', () => ({ userSubmitService: vi.fn() }))
vi.mock('@/apis/question', () => ({
  getQuestionDetailService: vi.fn(), preQuestionService: vi.fn(),
  nextQuestionService: vi.fn(), getQuestionResultService: vi.fn()
}))
vi.mock('@/apis/exam', () => ({
  examNextQuestionService: vi.fn(), examPreQuestionService: vi.fn(), getExamFirstQuestionService: vi.fn()
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { questionId: '1' } }) }))
vi.mock('@/router', () => ({ default: { go: vi.fn(), push: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), info: vi.fn() } }))
vi.mock('@/components/CodeEditor.vue', () => ({ default: {
  name: 'codeEditor', template: '<textarea />', methods: { setAceCode() {} }
} }))

let wrapper
beforeEach(async () => {
  vi.clearAllMocks()
  getQuestionDetailService.mockResolvedValue({ data: { questionVersion: 1, defaultCode: 'A' } })
  wrapper = shallowMount(Answer, { global: { stubs: { codeEditor: false } } })
  await flushPromises()
})
afterEach(() => wrapper.unmount())

// 模拟响应丢失后修改编辑器，“重试本次提交”仍需重发最初那份代码和幂等键。
test('retry keeps the original key and source after editor changes', async () => {
  userSubmitService.mockRejectedValueOnce(new Error('response lost'))
    .mockResolvedValueOnce({ data: { submitId: '123' } })
  wrapper.vm.handleEditorContent('source A')
  await wrapper.vm.submitQuestion()
  const original = userSubmitService.mock.calls[0][0]
  wrapper.vm.handleEditorContent('source B')
  await wrapper.vm.sendPendingRequest()
  expect(userSubmitService.mock.calls[1][0]).toEqual(original)
  expect(original.userCode).toBe('source A')
  expect(original.idempotencyKey).toBeTruthy()
})

// 与网络重试区分：主动新提交读取新代码，同时生成新的请求身份。
test('new submission uses the new source and a different key', async () => {
  userSubmitService.mockRejectedValue(new Error('response lost'))
  wrapper.vm.handleEditorContent('source A')
  await wrapper.vm.submitQuestion()
  const original = userSubmitService.mock.calls[0][0]
  wrapper.vm.handleEditorContent('source B')
  await wrapper.vm.submitQuestion()
  const next = userSubmitService.mock.calls[1][0]
  expect(next.userCode).toBe('source B')
  expect(next.idempotencyKey).not.toBe(original.idempotencyKey)
})

// 保持第一个 Promise 未完成，验证请求期间再次点击不会发出第二个请求。
test('an in-flight request prevents another submit', async () => {
  let finish
  userSubmitService.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  wrapper.vm.handleEditorContent('source A')
  const first = wrapper.vm.submitQuestion()
  await wrapper.vm.submitQuestion()
  expect(userSubmitService).toHaveBeenCalledTimes(1)
  finish({ data: { submitId: '123' } })
  await first
})
