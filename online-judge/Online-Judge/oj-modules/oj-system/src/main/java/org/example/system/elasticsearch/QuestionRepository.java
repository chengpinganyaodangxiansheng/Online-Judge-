package org.example.system.elasticsearch;

import org.example.system.domain.question.es.QuestionES;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * 继承自ElasticsearchRepository，提供基本的CRUD和分页功能
 * QuestionES：映射的Elasticsearch文档类型
 * Long：文档ID的类型
 * term查询	精确匹配，不进行分词处理（适合数字/枚举/keyword类型字段）
 * match查询	全文检索，对输入文本分词后匹配（适合text类型字段）
 * bool查询	组合查询，可包含must(AND)、should(OR)、must_not(NOT)等子句
 * minimum_should_match	控制至少满足的should条件数量（此处设为1表示OR逻辑）
 */
@Repository
public interface QuestionRepository extends ElasticsearchRepository<QuestionES, Long> {

    //根据难度值精确匹配查询问题  等效DSL：{
    //  "query": {
    //    "term": {
    //      "difficulty": 输入值
    //    }
    //  }
    //}
    Page<QuestionES> findQuestionByDifficulty(Integer difficulty, Pageable pageable);


    /**
     * should子句（或条件）：
     * 匹配title字段包含keywordTitle
     * 匹配content字段包含keywordContent
     * minimum_should_match: 1 表示至少满足一个条件
     * must子句（与条件）：
     * 精确匹配difficulty字段等于输入值
     * @param keywordTitle
     * @param keywordContent
     * @param difficulty
     * @param pageable
     * @return
     */
    //select  * from tb_question where (title like '%aaa%' or content like '%bbb%')  and difficulty = 1
    @Query("{\"bool\": {\"should\": [{ \"match\": { \"title\": \"?0\" } }, { \"match\": { \"content\": \"?1\" } }], \"minimum_should_match\": 1, \"must\": [{\"term\": {\"difficulty\": \"?2\"}}]}}")
    Page<QuestionES> findByTitleOrContentAndDifficulty(String keywordTitle, String keywordContent,Integer difficulty,  Pageable pageable);


    /**
     * 作用：查询标题或内容包含关键词的问题（不限难度）
     * 逻辑：与前述查询的should部分相同
     * 参数?0和?1分别对应方法的前两个参数
     * @param keywordTitle
     * @param keywordContent
     * @param pageable
     * @return
     */
    @Query("{\"bool\": {\"should\": [{ \"match\": { \"title\": \"?0\" } }, { \"match\": { \"content\": \"?1\" } }], \"minimum_should_match\": 1}}")
    Page<QuestionES> findByTitleOrContent(String keywordTitle, String keywordContent, Pageable pageable);

}