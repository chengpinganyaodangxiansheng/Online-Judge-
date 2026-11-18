import service from "@/utils/request";

export function getQuestionListService(params) {
  return service({
    url: "/question/semiLogin/list",
    method: "get",
    params,
  });
}

export function getHotQuestionListService() {
  return service({
    url: "/question/semiLogin/hotList",
    method: "get"
  });
}

// 比赛携带 examId 获取冻结规则，练习省略它获取当前版本。
export function getQuestionDetailService(questionId, examId) {
  return service({
    url: "/question/detail",
    method: "get",
    params: { questionId, examId },
  });
}

export function preQuestionService(questionId) {
  return service({
    url: "/question/preQuestion",
    method: "get",
    params: { questionId },
  });
}

export function nextQuestionService(questionId) {
  return service({
    url: "/question/nextQuestion",
    method: "get",
    params: { questionId },
  });
}

// 按服务端确认的唯一任务编号查询，结果不再通过题目和提交时间匹配。
export function getQuestionResultService(submitId) {
  return service({
    url: `/user/question/submissions/${submitId}`,
    method: "get",
  });
}
