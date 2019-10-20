/***********************************************************
 * @Description : 考试服务接口实现
 * @author      : 梁山广(Laing Shan Guang)
 * @date        : 2019-05-28 08:06
 * @email       : liangshanguang2@gmail.com
 ***********************************************************/
package com.huawei.l00379880.exam.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.huawei.l00379880.exam.entity.Exam;
import com.huawei.l00379880.exam.entity.Question;
import com.huawei.l00379880.exam.entity.QuestionOption;
import com.huawei.l00379880.exam.enums.QuestionEnum;
import com.huawei.l00379880.exam.repository.*;
import com.huawei.l00379880.exam.service.ExamService;
import com.huawei.l00379880.exam.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ExamServiceImpl implements ExamService {

    private final ExamRepository examRepository;

    private final QuestionRepository questionRepository;

    private final UserRepository userRepository;

    private final QuestionLevelRepository questionLevelRepository;

    private final QuestionTypeRepository questionTypeRepository;

    private final QuestionCategoryRepository questionCategoryRepository;

    private final QuestionOptionRepository questionOptionRepository;

    public ExamServiceImpl(QuestionRepository questionRepository, UserRepository userRepository, QuestionLevelRepository questionLevelRepository, QuestionTypeRepository questionTypeRepository, QuestionCategoryRepository questionCategoryRepository, QuestionOptionRepository questionOptionRepository, ExamRepository examRepository) {
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.questionLevelRepository = questionLevelRepository;
        this.questionTypeRepository = questionTypeRepository;
        this.questionCategoryRepository = questionCategoryRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.examRepository = examRepository;
    }

    @Override
    public QuestionPageVo getQuestionList(Integer pageNo, Integer pageSize) {
        // 按照日期降序排列
        Sort sort = new Sort(Sort.Direction.DESC, "updateTime");
        // 构造分页请求,注意前端面页面的分页是从1开始的，后端是从0开始地，所以要减去1哈
        PageRequest pageRequest = PageRequest.of(pageNo - 1, pageSize, sort);
        Page<Question> questionPage = questionRepository.findAll(pageRequest);
        QuestionPageVo questionPageVo = new QuestionPageVo();
        // 设置页码
        questionPageVo.setPageNo(pageNo);
        // 设置页大小
        questionPageVo.setPageSize(pageSize);
        // 设置总共有多少个元素
        questionPageVo.setTotalCount(questionPage.getTotalElements());
        // 设置一共有多少页
        questionPageVo.setTotalPage(questionPage.getTotalPages());
        // 当前页的问题列表
        List<Question> questionList = questionPage.getContent();
        // 需要自定义的question列表
        List<QuestionVo> questionVoList = new ArrayList<>();
        // 循环完成每个属性的定制
        for (Question question : questionList) {
            QuestionVo questionVo = new QuestionVo();
            // 先复制能复制的属性
            BeanUtils.copyProperties(question, questionVo);
            // 设置问题的创建者
            questionVo.setQuestionCreator(
                    Objects.requireNonNull(
                            userRepository.findById(
                                    question.getQuestionCreatorId()
                            ).orElse(null)
                    ).getUserUsername());

            // 设置问题的难度
            questionVo.setQuestionLevel(
                    Objects.requireNonNull(
                            questionLevelRepository.findById(
                                    question.getQuestionLevelId()
                            ).orElse(null)
                    ).getQuestionLevelDescription());

            // 设置题目的类别，比如单选、多选、判断等
            questionVo.setQuestionType(
                    Objects.requireNonNull(
                            questionTypeRepository.findById(
                                    question.getQuestionTypeId()
                            ).orElse(null)
                    ).getQuestionTypeDescription());

            // 设置题目分类，比如数学、语文、英语、生活、人文等
            questionVo.setQuestionCategory(
                    Objects.requireNonNull(
                            questionCategoryRepository.findById(
                                    question.getQuestionCategoryId()
                            ).orElse(null)
                    ).getQuestionCategoryName()
            );

            // 选项的自定义Vo列表
            List<QuestionOptionVo> optionVoList = new ArrayList<>();

            // 获得所有的选项列表
            List<QuestionOption> optionList = questionOptionRepository.findAllById(
                    Arrays.asList(question.getQuestionOptionIds().split("-"))
            );

            // 获取所有的答案列表optionList中每个option的isAnswer选项
            List<QuestionOption> answerList = questionOptionRepository.findAllById(
                    Arrays.asList(question.getQuestionAnswerOptionIds().split("-"))
            );

            // 根据选项和答案的id相同设置optionVo的isAnswer属性
            for (QuestionOption option : optionList) {
                QuestionOptionVo optionVo = new QuestionOptionVo();
                BeanUtils.copyProperties(option, optionVo);
                for (QuestionOption answer : answerList) {
                    if (option.getQuestionOptionId().equals(answer.getQuestionOptionId())) {
                        optionVo.setAnswer(true);
                    }
                }
                optionVoList.add(optionVo);
            }

            // 设置题目的所有选项
            questionVo.setQuestionOptionVoList(optionVoList);

            questionVoList.add(questionVo);
        }
        questionPageVo.setQuestionVoList(questionVoList);
        return questionPageVo;
    }

    @Override
    public void updateQuestion(QuestionVo questionVo) {
        // 1.把需要的属性都设置好
        String questionName = questionVo.getQuestionName();
        StringBuilder questionAnswerOptionIds = new StringBuilder();
        List<QuestionOption> questionOptionList = new ArrayList<>();
        List<QuestionOptionVo> questionOptionVoList = questionVo.getQuestionOptionVoList();
        int size = questionOptionVoList.size();
        for (int i = 0; i < questionOptionVoList.size(); i++) {
            QuestionOptionVo questionOptionVo = questionOptionVoList.get(i);
            QuestionOption questionOption = new QuestionOption();
            BeanUtils.copyProperties(questionOptionVo, questionOption);
            questionOptionList.add(questionOption);
            if (questionOptionVo.getAnswer()) {
                if (i != size - 1) {
                    // 把更新后的答案的id加上去,记得用-连到一起
                    questionAnswerOptionIds.append(questionOptionVo.getQuestionOptionId()).append("-");
                } else {
                    // 最后一个不需要用-连接
                    questionAnswerOptionIds.append(questionOptionVo.getQuestionOptionId());
                }
            }

        }

        // 1.更新问题
        Question question = questionRepository.findById(questionVo.getQuestionId()).orElse(null);
        assert question != null;
        question.setQuestionName(questionName);
        question.setQuestionAnswerOptionIds(questionAnswerOptionIds.toString());
        questionRepository.save(question);

        // 2.更新所有的option
        questionOptionRepository.saveAll(questionOptionList);
    }

    @Override
    public void questionCreate(QuestionCreateVo questionCreateVo) {
        // 问题创建
        Question question = new Question();
        // 把能复制的属性都复制过来
        BeanUtils.copyProperties(questionCreateVo, question);
        // 设置下questionOptionIds和questionAnswerOptionIds，需要自己用Hutool生成下
        List<QuestionOption> questionOptionList = new ArrayList<>();
        List<QuestionOptionCreateVo> questionOptionCreateVoList = questionCreateVo.getQuestionOptionCreateVoList();
        for (QuestionOptionCreateVo questionOptionCreateVo : questionOptionCreateVoList) {
            QuestionOption questionOption = new QuestionOption();
            // 设置选项的的内容
            questionOption.setQuestionOptionContent(questionOptionCreateVo.getQuestionOptionContent());
            // 设置选项的id
            questionOption.setQuestionOptionId(IdUtil.simpleUUID());
            questionOptionList.add(questionOption);
        }
        // 把选项都存起来，然后才能用于下面设置Question的questionOptionIds和questionAnswerOptionIds
        questionOptionRepository.saveAll(questionOptionList);
        String questionOptionIds = "";
        String questionAnswerOptionIds = "";
        // 经过上面的saveAll方法，所有的option的主键id都已经持久化了
        for (int i = 0; i < questionOptionCreateVoList.size(); i++) {
            // 获取指定选项
            QuestionOptionCreateVo questionOptionCreateVo = questionOptionCreateVoList.get(i);
            // 获取保存后的指定对象
            QuestionOption questionOption = questionOptionList.get(i);
            questionOptionIds += questionOption.getQuestionOptionId() + "-";
            if (questionOptionCreateVo.getAnswer()) {
                // 如果是答案的话
                questionAnswerOptionIds += questionOption.getQuestionOptionId() + "-";
            }
        }
        // 把字符串最后面的"-"给去掉
        questionAnswerOptionIds = replaceLastSeperator(questionAnswerOptionIds);
        questionOptionIds = replaceLastSeperator(questionOptionIds);
        // 设置选项id组成的字符串
        question.setQuestionOptionIds(questionOptionIds);
        // 设置答案选项id组成的字符串
        question.setQuestionAnswerOptionIds(questionAnswerOptionIds);
        // 自己生成问题的id
        question.setQuestionId(IdUtil.simpleUUID());
        // 保存问题到数据库
        questionRepository.save(question);
    }

    @Override
    public QuestionSelectionVo getSelections() {
        QuestionSelectionVo questionSelectionVo = new QuestionSelectionVo();
        questionSelectionVo.setQuestionCategoryList(questionCategoryRepository.findAll());
        questionSelectionVo.setQuestionLevelList(questionLevelRepository.findAll());
        questionSelectionVo.setQuestionTypeList(questionTypeRepository.findAll());

        return questionSelectionVo;
    }

    /**
     * 去除字符串最后的，防止split的时候出错
     *
     * @param str 原始字符串
     * @return
     */
    public static String trimMiddleLine(String str) {
        if (str.charAt(str.length() - 1) == '-') {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    @Override
    public QuestionDetailVo getQuestionDetail(String id) {
        Question question = questionRepository.findById(id).orElse(null);
        QuestionDetailVo questionDetailVo = new QuestionDetailVo();
        questionDetailVo.setId(id);
        questionDetailVo.setName(question.getQuestionName());
        questionDetailVo.setDescription(question.getQuestionDescription());
        // 问题类型，单选题/多选题/判断题
        questionDetailVo.setType(
                Objects.requireNonNull(
                        questionTypeRepository.findById(
                                question.getQuestionTypeId()
                        ).orElse(null)
                ).getQuestionTypeDescription()
        );
        // 获取当前问题的选项
        String optionIdsStr = trimMiddleLine(question.getQuestionOptionIds());
        String[] optionIds = optionIdsStr.split("-");
        // 获取选项列表
        List<QuestionOption> optionList = questionOptionRepository.findAllById(Arrays.asList(optionIds));
        questionDetailVo.setOptions(optionList);
        return questionDetailVo;
    }

    @Override
    public ExamPageVo getExamList(Integer pageNo, Integer pageSize) {
        // 获取考试列表
        // 按照日期降序排列
        Sort sort = new Sort(Sort.Direction.DESC, "updateTime");
        // 构造分页请求,注意前端面页面的分页是从1开始的，后端是从0开始地，所以要减去1哈
        PageRequest pageRequest = PageRequest.of(pageNo - 1, pageSize, sort);
        Page<Exam> examPage = examRepository.findAll(pageRequest);
        ExamPageVo examPageVo = new ExamPageVo();
        // 设置页码
        examPageVo.setPageNo(pageNo);
        // 设置每页有多少条数据
        examPageVo.setPageSize(pageSize);
        // 设置总共有多少个元素
        examPageVo.setTotalCount(examPage.getTotalElements());
        // 设置一共有多少页
        examPageVo.setTotalPage(examPage.getTotalPages());
        // 取出当前页的考试列表
        List<Exam> examList = examPage.getContent();
        // 需要自定义的exam列表
        List<ExamVo> examVoList = new ArrayList<>();
        // 循环完成每个属性的定制
        for (Exam exam : examList) {
            ExamVo examVo = new ExamVo();
            // 先尽量复制能复制的所有属性
            BeanUtils.copyProperties(exam, examVo);
            // 设置问题的创建者
            examVo.setExamCreator(
                    Objects.requireNonNull(
                            userRepository.findById(
                                    exam.getExamCreatorId()
                            ).orElse(null)
                    ).getUserUsername()
            );

            // 获取所有单选题列表，并赋值到ExamVo的属性ExamQuestionSelectVoRadioList上
            List<ExamQuestionSelectVo> radioQuestionVoList = new ArrayList<>();
            List<Question> radioQuestionList = questionRepository.findAllById(
                    Arrays.asList(exam.getExamQuestionIdsRadio().split("-"))
            );
            for (Question question : radioQuestionList) {
                ExamQuestionSelectVo radioQuestionVo = new ExamQuestionSelectVo();
                BeanUtils.copyProperties(question, radioQuestionVo);
                radioQuestionVoList.add(radioQuestionVo);
            }
            examVo.setExamQuestionSelectVoRadioList(radioQuestionVoList);

            // 获取所有多选题列表，并赋值到ExamVo的属性ExamQuestionSelectVoCheckList上
            List<ExamQuestionSelectVo> checkQuestionVoList = new ArrayList<>();
            List<Question> checkQuestionList = questionRepository.findAllById(
                    Arrays.asList(exam.getExamQuestionIdsCheck().split("-"))
            );
            for (Question question : checkQuestionList) {
                ExamQuestionSelectVo checkQuestionVo = new ExamQuestionSelectVo();
                BeanUtils.copyProperties(question, checkQuestionVo);
                checkQuestionVoList.add(checkQuestionVo);
            }
            examVo.setExamQuestionSelectVoCheckList(checkQuestionVoList);

            // 获取所有多选题列表，并赋值到ExamVo的属性ExamQuestionSelectVoJudgeList上
            List<ExamQuestionSelectVo> judgeQuestionVoList = new ArrayList<>();
            List<Question> judgeQuestionList = questionRepository.findAllById(
                    Arrays.asList(exam.getExamQuestionIdsJudge().split("-"))
            );
            for (Question question : judgeQuestionList) {
                ExamQuestionSelectVo judgeQuestionVo = new ExamQuestionSelectVo();
                BeanUtils.copyProperties(question, judgeQuestionVo);
                judgeQuestionVoList.add(judgeQuestionVo);
            }
            examVo.setExamQuestionSelectVoJudgeList(judgeQuestionVoList);

            // 把examVo加到examVoList中
            examVoList.add(examVo);
        }
        examPageVo.setExamVoList(examVoList);
        return examPageVo;
    }

    @Override
    public ExamQuestionTypeVo getExamQuestionType() {
        ExamQuestionTypeVo examQuestionTypeVo = new ExamQuestionTypeVo();
        // 获取所有单选题列表，并赋值到ExamVo的属性ExamQuestionSelectVoRadioList上
        List<ExamQuestionSelectVo> radioQuestionVoList = new ArrayList<>();
        List<Question> radioQuestionList = questionRepository.findByQuestionTypeId(QuestionEnum.RADIO.getId());
        for (Question question : radioQuestionList) {
            ExamQuestionSelectVo radioQuestionVo = new ExamQuestionSelectVo();
            BeanUtils.copyProperties(question, radioQuestionVo);
            radioQuestionVoList.add(radioQuestionVo);
        }
        examQuestionTypeVo.setExamQuestionSelectVoRadioList(radioQuestionVoList);

        // 获取所有多选题列表，并赋值到ExamVo的属性ExamQuestionSelectVoCheckList上
        List<ExamQuestionSelectVo> checkQuestionVoList = new ArrayList<>();
        List<Question> checkQuestionList = questionRepository.findByQuestionTypeId(QuestionEnum.CHECK.getId());
        for (Question question : checkQuestionList) {
            ExamQuestionSelectVo checkQuestionVo = new ExamQuestionSelectVo();
            BeanUtils.copyProperties(question, checkQuestionVo);
            checkQuestionVoList.add(checkQuestionVo);
        }
        examQuestionTypeVo.setExamQuestionSelectVoCheckList(checkQuestionVoList);

        // 获取所有多选题列表，并赋值到ExamVo的属性ExamQuestionSelectVoJudgeList上
        List<ExamQuestionSelectVo> judgeQuestionVoList = new ArrayList<>();
        List<Question> judgeQuestionList = questionRepository.findByQuestionTypeId(QuestionEnum.JUDGE.getId());
        for (Question question : judgeQuestionList) {
            ExamQuestionSelectVo judgeQuestionVo = new ExamQuestionSelectVo();
            BeanUtils.copyProperties(question, judgeQuestionVo);
            judgeQuestionVoList.add(judgeQuestionVo);
        }
        examQuestionTypeVo.setExamQuestionSelectVoJudgeList(judgeQuestionVoList);
        return examQuestionTypeVo;
    }

    @Override
    public Exam create(ExamCreateVo examCreateVo, String userId) {
        // 在线考试系统创建
        Exam exam = new Exam();
        BeanUtils.copyProperties(examCreateVo, exam);
        exam.setExamId(IdUtil.simpleUUID());
        exam.setExamCreatorId(userId);
        String radioIdsStr = "";
        String checkIdsStr = "";
        String judgeIdsStr = "";
        List<ExamQuestionSelectVo> radios = examCreateVo.getRadios();
        List<ExamQuestionSelectVo> checks = examCreateVo.getChecks();
        List<ExamQuestionSelectVo> judges = examCreateVo.getJudges();
        int radioCnt = 0, checkCnt = 0, judgeCnt = 0;
        for (ExamQuestionSelectVo radio : radios) {
            if (radio.getChecked()) {
                radioIdsStr += radio.getQuestionId() + "-";
                radioCnt++;
            }
        }
        radioIdsStr = replaceLastSeperator(radioIdsStr);
        for (ExamQuestionSelectVo check : checks) {
            if (check.getChecked()) {
                checkIdsStr += check.getQuestionId() + "-";
                checkCnt++;
            }
        }
        checkIdsStr = replaceLastSeperator(checkIdsStr);
        for (ExamQuestionSelectVo judge : judges) {
            if (judge.getChecked()) {
                judgeIdsStr += judge.getQuestionId() + "-";
                judgeCnt++;
            }
        }
        judgeIdsStr = replaceLastSeperator(judgeIdsStr);
        exam.setExamQuestionIds(radioIdsStr + "-" + checkIdsStr + "-" + judgeIdsStr);
        // 设置各个题目的id
        exam.setExamQuestionIdsRadio(radioIdsStr);
        exam.setExamQuestionIdsCheck(checkIdsStr);
        exam.setExamQuestionIdsJudge(judgeIdsStr);

        // 计算总分数
        int examScore = radioCnt * exam.getExamScoreRadio() + checkCnt * exam.getExamScoreCheck() + judgeCnt * exam.getExamScoreJudge();
        exam.setExamScore(examScore);
        examRepository.save(exam);
        return exam;
    }

    @Override
    public List<ExamCardVo> getExamCardList() {
        List<Exam> examList = examRepository.findAll();
        List<ExamCardVo> examCardVoList = new ArrayList<>();
        for (Exam exam : examList) {
            ExamCardVo examCardVo = new ExamCardVo();
            BeanUtils.copyProperties(exam, examCardVo);
            examCardVoList.add(examCardVo);
        }
        return examCardVoList;
    }

    @Override
    public ExamDetailVo getExamDetail(String id) {
        Exam exam = examRepository.findById(id).orElse(null);
        ExamDetailVo examDetailVo = new ExamDetailVo();
        examDetailVo.setExam(exam);
        examDetailVo.setRadioIds(exam.getExamQuestionIdsRadio().split("-"));
        examDetailVo.setCheckIds(exam.getExamQuestionIdsCheck().split("-"));
        examDetailVo.setJudgeIds(exam.getExamQuestionIdsJudge().split("-"));
        return examDetailVo;
    }

    /**
     * 把字符串最后一个字符-替换掉
     *
     * @param str 原始字符串
     * @return 替换掉最后一个-的字符串
     */
    private String replaceLastSeperator(String str) {
        String lastChar = str.substring(str.length() - 1);
        if ("-".equals(lastChar)) {
            str = StrUtil.sub(str, 0, str.length() - 1);
        }
        return str;
    }
}

























