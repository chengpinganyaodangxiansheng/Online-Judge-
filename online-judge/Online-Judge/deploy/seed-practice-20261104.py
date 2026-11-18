"""生成九道本地练习题及题解；--verify 验证 Java；--import-local 导入本地容器。

仅使用 Python 标准库。固定编号并校验已存在记录，重复执行不会覆盖原题。
"""
import argparse
import collections
import datetime
import json
import pathlib
import random
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent
OUT = ROOT / 'practice-20261104'
QUESTIONS = []


def add(title, difficulty, method, returns, params, statement, constraints, samples,
        idea, proof, complexity, body, cases, oracle, extra=False, string=False):
    qid = 202611040001 + len(QUESTIONS)
    signature = f'public static {returns} {method}({params})'
    if string:
        main = f'public static void main(String[] args) {{ System.out.println({method}(args[0])); }}'
    else:
        tail = ', Integer.parseInt(args[1])' if extra else ''
        main = ('public static void main(String[] args) {\n'
                '    int[] a = java.util.Arrays.stream(args[0].split(",")).mapToInt(Integer::parseInt).toArray();\n'
                f'    System.out.println({method}(a{tail}));\n}}')
    default = f'public class Solution {{\n    {signature} {{\n        // 请在这里编写解法\n        return {"false" if returns == "boolean" else "0"};\n    }}\n}}'
    answer = f'public class Solution {{\n    {signature} {{\n{body}\n    }}\n}}'
    content = (f'<h3>题目描述</h3><p>{statement}</p><h3>约束</h3><p>{constraints}</p>'
               f'<h3>接口</h3><pre>{signature}</pre><p>在 Solution 类中实现上述静态方法；入口由平台提供，无需自行读取标准输入或编写 main。</p>'
               f'<h3>示例</h3><pre>{samples}</pre>')
    tests = [{'input': inp, 'output': str(oracle(inp)).lower()} for inp in cases]
    q = dict(questionId=qid, title=title, difficulty=difficulty, timeLimit=2000,
             spaceLimit=262144, content=content, questionCase=json.dumps(tests, ensure_ascii=False, separators=(',', ':')),
             defaultCode=default, mainFuc=main, questionVersion=1)
    for key, limit in [('content', 1000), ('questionCase', 1000), ('defaultCode', 500), ('mainFuc', 500)]:
        assert len(q[key]) <= limit, (title, key, len(q[key]))
    QUESTIONS.append(dict(row=q, answer=answer, idea=idea, proof=proof, complexity=complexity,
                          statement=statement, constraints=constraints, samples=samples, oracle=oracle,
                          cases=cases, extra=extra, string=string, method=method))


def array(inp):
    return list(map(int, inp.split()[0].split(',')))


def distinct(inp):
    return len(set(array(inp)))


def profit(inp):
    a = array(inp)
    return max([0] + [a[j] - a[i] for i in range(len(a)) for j in range(i + 1, len(a))])


def brackets(inp):
    while any(p in inp for p in ['()', '[]', '{}']):
        for p in ['()', '[]', '{}']:
            inp = inp.replace(p, '')
    return not inp


def subcount(inp):
    a, k = array(inp), int(inp.split()[1])
    return sum(sum(a[i:j]) == k for i in range(len(a)) for j in range(i + 1, len(a) + 1))


def lis(inp):
    a = array(inp)
    dp = [1] * len(a)
    for i in range(len(a)):
        dp[i] = 1 + max([0] + [dp[j] for j in range(i) if a[j] < a[i]])
    return max(dp)


def coins(inp):
    a, n = array(inp), int(inp.split()[1])
    # 使用 BFS 作独立校验，区别于参考代码的动态规划。
    queue, seen = collections.deque([(0, 0)]), {0}
    while queue:
        s, step = queue.popleft()
        if s == n:
            return step
        for v in a:
            if s + v <= n and s + v not in seen:
                seen.add(s + v)
                queue.append((s + v, step + 1))
    return -1


def water(inp):
    a = array(inp)
    return sum(max(0, min(max(a[:i+1]), max(a[i:])) - a[i]) for i in range(len(a)))


def rectangle(inp):
    a = array(inp)
    return max(min(a[i:j]) * (j-i) for i in range(len(a)) for j in range(i+1, len(a)+1))


def shortest(inp):
    a, k = array(inp), int(inp.split()[1])
    return min([j-i for i in range(len(a)) for j in range(i+1, len(a)+1) if sum(a[i:j]) >= k], default=-1)


add('有序数组去重计数', 1, 'countDistinct', 'int', 'int[] nums',
    '给定按非递减顺序排列的整数数组 nums，返回其中不同整数的个数。无需修改数组。',
    '1 ≤ nums.length ≤ 200000；-10^9 ≤ nums[i] ≤ 10^9。',
    'nums = [1,1,2,2,3] → 3\nnums = [-2,-2,-2] → 1',
    '有序数组中相同元素必然相邻。先计入第一个元素，再统计与前一个值不同的位置。',
    '每个不同值恰好形成一个连续分组；首元素和每次值变化分别对应一个分组的开始，因此计数等于不同值个数。',
    '时间 O(n)，额外空间 O(1)。',
    '''        int count = 1;
        for (int i = 1; i < nums.length; i++) {
            if (nums[i] != nums[i - 1]) count++;
        }
        return count;''',
    ['1', '1,1,2,2,3', '-2,-2,-2', '-3,-1,0,2', '-1000000000,0,1000000000'], distinct)

add('一次买卖的最大收益', 1, 'maxProfit', 'int', 'int[] prices',
    'prices[i] 表示第 i 天的价格。最多买入并卖出一次，且买入日必须早于卖出日。返回最大收益；可以不交易，此时收益为 0。',
    '1 ≤ prices.length ≤ 200000；0 ≤ prices[i] ≤ 10^9。',
    'prices = [7,1,5,3,6,4] → 5\nprices = [7,6,4,3,1] → 0',
    '顺序扫描，维护已遇到的最低价格，以当前价格减去最低价格更新最大收益。',
    '对每个卖出日，之前最低价格对应当天能获得的最优收益；遍历所有卖出日后得到全局最优。收益初始为 0，涵盖不交易的情况。',
    '时间 O(n)，额外空间 O(1)。',
    '''        int low = prices[0], best = 0;
        for (int price : prices) {
            best = Math.max(best, price - low);
            low = Math.min(low, price);
        }
        return best;''',
    ['5', '7,1,5,3,6,4', '7,6,4,3,1', '2,2,2', '0,1000000000', '3,1,2'], profit)

add('括号序列校验', 1, 'isValid', 'boolean', 'String s',
    '字符串 s 仅含 ()[]{}。判断所有括号能否按类型正确配对，且嵌套顺序正确。返回 true 或 false。',
    '1 ≤ s.length ≤ 200000。',
    's = "([]{})" → true\ns = "([)]" → false',
    '用字符栈存储尚未匹配的左括号。遇到右括号时，只能与栈顶的对应左括号配对。',
    '栈始终按出现顺序保存未闭合的左括号。合法嵌套要求最后出现的左括号最先闭合，因此栈顶不匹配时必定非法；扫描结束栈空则全部正确配对。',
    '时间 O(n)，额外空间 O(n)。',
    '''        char[] stack = new char[s.length()];
        int top = 0;
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') stack[top++] = c;
            else {
                char want = c == ')' ? '(' : c == ']' ? '[' : '{';
                if (top == 0 || stack[--top] != want) return false;
            }
        }
        return top == 0;''',
    ['([]{})', '([)]', '(', ']', '()[]{}', '{{[()]}}', '((', '}{'], brackets, string=True)

add('和为目标值的连续子数组', 2, 'countSubarrays', 'long', 'int[] nums, int k',
    '给定整数数组 nums 和整数 k，返回元素和恰好等于 k 的非空连续子数组个数。元素可能为负数，返回值使用 long。',
    '1 ≤ nums.length ≤ 200000；-10^9 ≤ nums[i], k ≤ 10^9。',
    'nums = [1,1,1], k = 2 → 2\nnums = [0,0,0], k = 0 → 6',
    '维护 long 前缀和及其出现次数。当前前缀和为 sum 时，之前值为 sum-k 的每个前缀都对应一个满足要求的子数组。先查询再登记当前前缀。',
    '子数组 [l,r) 的和等于 prefix[r]-prefix[l]。计数表只保存 r 之前的前缀，所以恰好统计所有非空合法子数组且不会重复。初始登记前缀 0，涵盖从首元素开始的子数组。',
    '期望时间 O(n)，额外空间 O(n)。前缀和与答案都使用 long。',
    '''        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        counts.put(0L, 1L);
        long sum = 0, answer = 0;
        for (int value : nums) {
            sum += value;
            answer += counts.getOrDefault(sum - k, 0L);
            counts.put(sum, counts.getOrDefault(sum, 0L) + 1);
        }
        return answer;''',
    ['1,1,1 2', '0,0,0 0', '1,-1,0 0', '5 5', '1,2 9', '1000000000,1000000000,-1000000000 1000000000'], subcount, extra=True)

add('最长严格递增子序列', 2, 'lengthOfLIS', 'int', 'int[] nums',
    '从 nums 中按原顺序选择若干元素，要求后一个元素严格大于前一个元素。返回能选出的最长子序列长度；所选元素无需连续。',
    '1 ≤ nums.length ≤ 200000；-10^9 ≤ nums[i] ≤ 10^9；要求 O(n log n) 时间。',
    'nums = [10,9,2,5,3,7,101,18] → 4\nnums = [2,2,2] → 1',
    'tails[i] 维护长度为 i+1 的递增子序列能取得的最小尾值。对每个元素二分查找第一个大于等于它的位置并替换；找不到则扩展长度。',
    '更小的尾值不会减少未来可接入的元素。二分得到的位置前面均小于当前值，故可构造对应长度的严格递增子序列；替换保持最小尾值不变量。最终 tails 的有效长度即最优长度。',
    '时间 O(n log n)，额外空间 O(n)。使用 lower_bound，重复元素不会扩展长度。',
    '''        int[] tails = new int[nums.length];
        int size = 0;
        for (int x : nums) {
            int lo = 0, hi = size;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (tails[mid] < x) lo = mid + 1;
                else hi = mid;
            }
            tails[lo] = x;
            if (lo == size) size++;
        }
        return size;''',
    ['10,9,2,5,3,7,101,18', '2,2,2', '5,4,3,2,1', '-3,-2,-1,0', '9', '1,3,2,4,3,5'], lis)

add('凑出金额的最少硬币数', 2, 'coinChange', 'int', 'int[] coins, int amount',
    'coins 给出各类硬币的正整数面值，每类数量无限。返回恰好凑出 amount 所需的最少硬币数；无法凑出返回 -1；amount 为 0 时返回 0。',
    '1 ≤ coins.length ≤ 100；1 ≤ coins[i] ≤ 10000；0 ≤ amount ≤ 10000。允许重复面值。',
    'coins = [1,2,5], amount = 11 → 3\ncoins = [2], amount = 3 → -1',
    '令 dp[s] 为凑出金额 s 所需的最少硬币数，dp[0]=0。按金额递增遍历，枚举最后使用的硬币 c，以 dp[s-c]+1 更新。',
    '任意正金额的最优方案都有最后一枚硬币，去掉它后必须是剩余金额的最优方案。所有面值均为正，因此依赖金额更小，递增顺序可以求出完整最优解。',
    '时间 O(amount × coins.length)，额外空间 O(amount)。',
    '''        int[] dp = new int[amount + 1];
        java.util.Arrays.fill(dp, amount + 1);
        dp[0] = 0;
        for (int s = 1; s <= amount; s++) {
            for (int c : coins) {
                if (c <= s) dp[s] = Math.min(dp[s], dp[s - c] + 1);
            }
        }
        return dp[amount] > amount ? -1 : dp[amount];''',
    ['1,2,5 11', '2 3', '2 0', '2,5 10', '1 9', '1,3,4 6', '3,3 6', '10000 9999'], coins, extra=True)

add('雨后蓄水总量', 3, 'trap', 'long', 'int[] height',
    'height 表示一排宽度均为 1 的柱子高度。下雨后，返回柱子之间能够积存的水量。水不会从两端外侧积存，返回值使用 long。',
    '1 ≤ height.length ≤ 200000；0 ≤ height[i] ≤ 10^9；要求 O(n) 时间及 O(1) 额外空间。',
    'height = [0,1,0,2,1,0,1,3,2,1,2,1] → 6\nheight = [3,0,3] → 3',
    '使用左右指针以及左右已扫描区域的最大高度。每次处理当前较矮的一端：另一端已提供足够高的边界，可以直接累加本端最大高度与当前柱高的差。',
    '处理左端时若左侧历史最高柱超过当前右端，则该最高柱出现时右侧已有不低于它的边界，否则算法会先移动右端而不会越过该左柱。因此较矮端的历史最高值能作为有效水位；右端同理。每个位置恰好处理一次。',
    '时间 O(n)，额外空间 O(1)。累加水量使用 long，防止溢出。',
    '''        int left = 0, right = height.length - 1;
        int leftMax = 0, rightMax = 0;
        long answer = 0;
        while (left <= right) {
            if (height[left] <= height[right]) {
                leftMax = Math.max(leftMax, height[left]);
                answer += leftMax - height[left++];
            } else {
                rightMax = Math.max(rightMax, height[right]);
                answer += rightMax - height[right--];
            }
        }
        return answer;''',
    ['0,1,0,2,1,0,1,3,2,1,2,1', '3,0,3', '5', '1,2,3', '3,2,1', '4,2,0,3,2,5', '1000000000,0,0,0,1000000000'], water)

add('柱状图中的最大矩形', 3, 'largestRectangleArea', 'long', 'int[] heights',
    'heights 表示一排相邻柱子的高度，每根宽度为 1。选择连续若干根柱子，在它们覆盖的区间内找出完全位于柱状图内部的最大矩形面积，返回 long。',
    '1 ≤ heights.length ≤ 200000；0 ≤ heights[i] ≤ 10^9；要求 O(n) 时间。',
    'heights = [2,1,5,6,2,3] → 10\nheights = [2,2,2] → 6',
    '维护高度非递减的下标栈。遇到较矮柱子时，逐个弹出更高的柱子，以弹出柱高为矩形高度、当前下标为右开边界、弹出后的栈顶为左开边界计算面积。末尾补一个高度 0。',
    '弹出时，当前柱子是右侧第一个更矮边界；栈顶与当前下标之间的柱子均不低于弹出高度。等高柱子可能暂时限制左边界，但最左侧同高柱最终会枚举完整宽度。因此必然枚举到最优矩形，且所有计算出的矩形均合法。',
    '时间 O(n)，额外空间 O(n)。先将高度转为 long 再乘宽度。',
    '''        int n = heights.length, top = 0;
        int[] stack = new int[n];
        long best = 0;
        for (int i = 0; i <= n; i++) {
            int current = i == n ? 0 : heights[i];
            while (top > 0 && heights[stack[top - 1]] > current) {
                int h = heights[stack[--top]];
                int left = top == 0 ? -1 : stack[top - 1];
                best = Math.max(best, (long) h * (i - left - 1));
            }
            if (i < n) stack[top++] = i;
        }
        return best;''',
    ['2,1,5,6,2,3', '2,2,2', '0', '1,2,3,4', '4,3,2,1', '2,0,2', '1000000000,1000000000,1000000000'], rectangle)

add('含负数数组的最短达标区间', 3, 'shortestSubarray', 'int', 'int[] nums, int k',
    '给定可能含负数的整数数组 nums 和正整数 k，返回元素和大于等于 k 的最短非空连续子数组长度；不存在则返回 -1。',
    '1 ≤ nums.length ≤ 200000；-10^9 ≤ nums[i] ≤ 10^9；1 ≤ k ≤ 10^9；要求 O(n) 时间。',
    'nums = [2,-1,2], k = 3 → 3\nnums = [84,-37,32,40,95], k = 167 → 3',
    '计算 long 前缀和，维护前缀值严格递增的下标双端队列。当前前缀与队首的差达到 k 时更新答案并弹出队首；当前前缀不大于队尾时弹出队尾，最后加入当前下标。',
    '已达标的队首在未来只会得到更长区间，可以删除。如果较新的前缀和不大于较旧前缀和，则它对所有未来右端都能得到至少同样大的区间和且长度更短，旧下标可被永久淘汰。保留下标因此覆盖所有可能的最优解。',
    '时间 O(n)，额外空间 O(n)。每个下标最多入队一次、出队一次；前缀和使用 long。',
    '''        int n = nums.length;
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + nums[i];
        int[] deque = new int[n + 1];
        int head = 0, tail = 0, best = n + 1;
        for (int i = 0; i <= n; i++) {
            while (head < tail && prefix[i] - prefix[deque[head]] >= k) {
                best = Math.min(best, i - deque[head++]);
            }
            while (head < tail && prefix[deque[tail - 1]] >= prefix[i]) tail--;
            deque[tail++] = i;
        }
        return best == n + 1 ? -1 : best;''',
    ['2,-1,2 3', '84,-37,32,40,95 167', '1,2 4', '5 5', '-1,-2 1', '1,-1,3 3', '1000000000,1000000000 1000000000'], shortest, extra=True)


def docker(args, data=None):
    r = subprocess.run(['docker', *args], input=data, capture_output=True, encoding='utf-8')
    if r.returncode:
        raise RuntimeError(r.stderr)
    return r.stdout


MYSQL = ['exec', '-i', 'oj-reliability-mysql', 'sh', '-c',
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B --raw bitoj_dev']


def sql_value(value):
    if isinstance(value, int):
        return str(value)
    return "CONVERT(X'" + value.encode('utf-8').hex() + "' USING utf8mb4)"


def generate():
    OUT.mkdir(exist_ok=True)
    rows = [q['row'] for q in QUESTIONS]
    (OUT / 'questions.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    fields = ['questionId', 'title', 'difficulty', 'timeLimit', 'spaceLimit', 'content', 'questionCase', 'defaultCode', 'mainFuc', 'questionVersion']
    columns = ['question_id', 'title', 'difficulty', 'time_limit', 'space_limit', 'content', 'question_case', 'default_code', 'main_fuc', 'question_version']
    sql = ['-- UTF-8 data encoded as hex to avoid shell/client character conversion.', 'SET NAMES utf8mb4;', 'START TRANSACTION;']
    for q in rows:
        sql.append('INSERT INTO tb_question (' + ','.join(columns) + ',create_by,create_time) SELECT ' +
                   ','.join(sql_value(q[f]) for f in fields) + ',1,NOW() WHERE NOT EXISTS '
                   f'(SELECT 1 FROM tb_question WHERE question_id={q["questionId"]});')
    sql += ['COMMIT;']
    (OUT / 'questions.sql').write_text('\n'.join(sql) + '\n', encoding='utf-8')
    doc = ['# 算法擂台新增题目参考答案', '', '新增日期：2026-11-04。easy、mid、hard 各 3 题，共 9 题。', '',
           '所有答案均为 Java 17，可直接粘贴到平台编辑器。保留 `Solution` 类及对应静态方法即可，平台会自动加入入口方法。', '',
           '每题时限 2000 ms，内存限制 262144 KiB。以下题号与本地题库一致。', '',
           '| 题号 | 难度 | 题目 |', '| --- | --- | --- |']
    for q in QUESTIONS:
        r = q['row']
        doc.append(f'| {r["questionId"]} | {["", "easy", "mid", "hard"][r["difficulty"]]} | {r["title"]} |')
    for q in QUESTIONS:
        r = q['row']
        doc += ['', f'## {r["questionId"]} · {r["title"]}', '', q['statement'], '', '**约束：** ' + q['constraints'], '',
                '```text', q['samples'], '```', '', '**解题思路：** ' + q['idea'], '', '**正确性：** ' + q['proof'], '',
                '**复杂度：** ' + q['complexity'], '', '```java', q['answer'], '```', '',
                '**平台入口验证用例**（数组以逗号连接，额外整数参数用空格分隔）：', '', '| 输入参数 | 预期输出 |', '| --- | --- |']
        for c in json.loads(r['questionCase']):
            doc.append(f'| `{c["input"]}` | `{c["output"]}` |')
    (OUT / '新增9题参考答案.md').write_text('\n'.join(doc) + '\n', encoding='utf-8')


def verify():
    rng = random.Random(20261104)
    report = []
    with tempfile.TemporaryDirectory(prefix='oj-practice-') as directory:
        folder = pathlib.Path(directory)
        for q in QUESTIONS:
            tests = list(q['cases'])
            for _ in range(150):
                a = [rng.randint(-8, 8) for _ in range(rng.randint(1, 22))]
                method = q['method']
                if method == 'isValid':
                    inp = ''.join(rng.choice('()[]{}') for _ in a)
                else:
                    if method == 'countDistinct':
                        a.sort()
                    if method in ('maxProfit', 'trap', 'largestRectangleArea'):
                        a = list(map(abs, a))
                    if method == 'coinChange':
                        a = [abs(x) + 1 for x in a]
                    inp = ','.join(map(str, a))
                    if q['extra']:
                        k = rng.randint(0, 40) if method == 'coinChange' else rng.randint(1, 30) if method == 'shortestSubarray' else rng.randint(-15, 15)
                        inp += f' {k}'
                tests.append(inp)
            # JVM 内调用相同的平台入口，覆盖入口拼接、参数拆分、方法返回值。
            checks = []
            for i, inp in enumerate(tests):
                args = ','.join(json.dumps(x) for x in inp.split(' '))
                checks.append(f'        main(new String[]{{{args}}});')
            stress = {
                'countDistinct': 'int[] a = new int[200000]; for(int i=0;i<a.length;i++) a[i]=i; if(countDistinct(a)!=200000) throw new AssertionError();',
                'maxProfit': 'int[] a = new int[200000]; a[a.length-1]=1000000000; if(maxProfit(a)!=1000000000) throw new AssertionError();',
                'isValid': 'if(!isValid("(".repeat(100000)+")".repeat(100000))) throw new AssertionError();',
                'countSubarrays': 'if(countSubarrays(new int[200000],0)!=20000100000L) throw new AssertionError(); int[] a=new int[200000]; java.util.Arrays.fill(a,1000000000); if(countSubarrays(a,1000000000)!=200000L) throw new AssertionError();',
                'lengthOfLIS': 'int[] a=new int[200000]; for(int i=0;i<a.length;i++) a[i]=i; if(lengthOfLIS(a)!=200000) throw new AssertionError();',
                'coinChange': 'if(coinChange(new int[]{1,3,4},10000)!=2500 || coinChange(new int[]{9999},10000)!=-1) throw new AssertionError();',
                'trap': 'int[] a=new int[200000]; a[0]=a[a.length-1]=1000000000; if(trap(a)!=199998000000000L) throw new AssertionError();',
                'largestRectangleArea': 'int[] a=new int[200000]; java.util.Arrays.fill(a,1000000000); if(largestRectangleArea(a)!=200000000000000L) throw new AssertionError();',
                'shortestSubarray': 'int[] a=new int[200000]; java.util.Arrays.fill(a,1000000000); if(shortestSubarray(a,1000000000)!=1) throw new AssertionError(); java.util.Arrays.fill(a,-1); if(shortestSubarray(a,1)!=-1) throw new AssertionError();',
            }[q['method']]
            main = q['row']['mainFuc'] + '\npublic static void verifyAll() {\n' + '\n'.join(checks) + '\n' + stress + '\n}'
            code = q['answer'].rsplit('}', 1)[0] + main + '\n}'
            (folder / 'Solution.java').write_text(code, encoding='utf-8')
            (folder / 'Check.java').write_text('class Check { public static void main(String[] args) { Solution.verifyAll(); } }', encoding='utf-8')
            subprocess.run(['javac', '-encoding', 'UTF-8', 'Solution.java', 'Check.java'], cwd=folder, check=True, capture_output=True)
            result = subprocess.run(['java', '-cp', str(folder), 'Check'], cwd=folder, check=True, capture_output=True, text=True)
            expected = [str(q['oracle'](x)).lower() for x in tests]
            assert result.stdout.splitlines() == expected, q['row']['title']
            # 实际编辑器初始模板也须可编译（入口由后端拼入）。
            (folder / 'Solution.java').write_text(q['row']['defaultCode'].rsplit('}', 1)[0] + q['row']['mainFuc'] + '\n}', encoding='utf-8')
            subprocess.run(['javac', '-encoding', 'UTF-8', 'Solution.java'], cwd=folder, check=True, capture_output=True)
            report.append(f'{q["row"]["title"]}: {len(tests)} cases PASS; maximum-size/overflow checks PASS; starter compilation PASS')
    (OUT / 'verification.txt').write_text('\n'.join(report) + '\n', encoding='utf-8')
    print('\n'.join(report))


def import_local():
    # 已存在的固定题号必须是同一条题目；冲突时不覆盖。
    rows = [q['row'] for q in QUESTIONS]
    ids = ','.join(str(r['questionId']) for r in rows)
    fields = ['title', 'difficulty', 'timeLimit', 'spaceLimit', 'content', 'questionCase', 'defaultCode', 'mainFuc', 'questionVersion']
    cols = ['title', 'difficulty', 'time_limit', 'space_limit', 'content', 'question_case', 'default_code', 'main_fuc', 'question_version']
    query = "SELECT JSON_OBJECT('questionId',question_id," + ','.join(f"'{f}',{c}" for f, c in zip(fields, cols)) + f') FROM tb_question WHERE question_id IN ({ids});'
    before = docker(MYSQL, query)
    by_id = {r['questionId']: r for r in rows}
    for line in before.splitlines():
        current = json.loads(line)
        assert current == by_id[current['questionId']], '题号已存在且内容不同，停止导入'
    all_titles = docker(MYSQL, 'SELECT title FROM tb_question;').splitlines()
    for r in rows:
        assert r['title'] not in all_titles or str(r['questionId']) in before, '标题重复，停止导入'
    if before:
        (OUT / 'existing-seed-backup.jsonl').write_text(before, encoding='utf-8')
    docker(MYSQL, (OUT / 'questions.sql').read_text(encoding='utf-8'))
    actual = [json.loads(line) for line in docker(MYSQL, query).splitlines()]
    assert sorted(actual, key=lambda r: r['questionId']) == rows, '数据库回读不一致'
    now = datetime.datetime.now().strftime('%Y-%m-%dT%H:%M:%S')
    bulk = []
    for r in rows:
        # 与 QuestionES 的字段保持一致；实体无版本字段。
        document = {k: v for k, v in r.items() if k != 'questionVersion'}
        document['createTime'] = now
        bulk.extend([json.dumps({'index': {'_index': 'idx_question', '_id': str(r['questionId'])}}), json.dumps(document, ensure_ascii=False)])
    response = json.loads(docker(['exec', '-i', 'oj-es-dev', 'curl', '-fsS', '-H', 'Content-Type: application/x-ndjson',
                                  '-XPOST', 'localhost:9200/_bulk?refresh=true', '--data-binary', '@-'], '\n'.join(bulk) + '\n'))
    assert not response['errors'], response
    for r in rows:
        result = json.loads(docker(['exec', 'oj-es-dev', 'curl', '-fsS', f'localhost:9200/idx_question/_doc/{r["questionId"]}']))
        assert result['_source']['title'] == r['title'] and result['_source']['difficulty'] == r['difficulty']
    # 仅失效题目导航列表，下次访问由现有服务从数据库重建；不触碰登录缓存。
    assert docker(['exec', 'oj-redis', 'redis-cli', 'PING']).strip() == 'PONG'
    docker(['exec', 'oj-redis', 'redis-cli', 'DEL', 'q:l'])
    result = {'database': 'bitoj_dev', 'newQuestions': len(rows) - len(before.splitlines()), 'verifiedQuestions': len(rows),
              'difficultyCounts': {'easy': 3, 'mid': 3, 'hard': 3}, 'mysqlReadback': 'PASS', 'elasticsearchReadback': 'PASS', 'navigationCache': 'invalidated'}
    (OUT / 'import-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--import-local', action='store_true')
    args = parser.parse_args()
    generate()
    if args.verify:
        verify()
    if args.import_local:
        import_local()
