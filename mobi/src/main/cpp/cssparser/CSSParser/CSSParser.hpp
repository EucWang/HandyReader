//
//  CSSParser.hpp
//  DDCSSParser
//
//  Created by 1m0nster on 2018/8/7.
//  Copyright © 2018 1m0nster. All rights reserved.
//

#ifndef CSSParser_hpp
#define CSSParser_hpp

#include <stdio.h>
#include <iostream>
#include <stack>
#include <vector>
#include "CSSLex.hpp"
#include "CSSParserStatus.h"
#include "Selectors/SelectorsHeader.h"
#include "Keyword/KeywordItem.hpp"
#include "Selectors/PseudoSelector.hpp"

namespace future {
    class Lex;
    class Selector;
    class CSSParser {
    public:
        struct ASTNode {
            Selector* head;
            ASTNode* left;
            ASTNode* right;
            ASTNode()
            {
                head = NULL;
                left = NULL;
                right = NULL;
            }
        };
    public:
        CSSParser();
        ~CSSParser();
        /**
         * Start parsing a css file
         */
        bool                        parseByFile(const std::string& cssFile);
        
        /**
         * Start parsing css string
         */
        bool                        parseByString (const std::string& cssString);
        
        /**
         * Get the selector models
         *
         * v4.0.1 修复:返回插入顺序(CSS 源码出现顺序)的 vector 引用,
         * 不再按指针地址排序。这是 CSS cascade 的自然 tiebreaker:
         * 源码顺序靠后的规则胜出,与浏览器一致。
         * 返回 const 引用避免每次调用拷贝整个容器。
         */
        const std::vector<Selector *>&  getSelectors();
        
        /**
         * Get the Keyworld models
         */
        std::list<KeywordItem *>    getKeywords();

        void 							 cleanRes();

    private:
        typedef void(*treeTranverseAction)(ASTNode *);
        typedef CSSParser::ASTNode *(*treeTranverseWithUserDataAction)(std::stack<CSSParser::ASTNode *>* stack);
        friend CSSParser::ASTNode* TreeTranverseCreateExpressionAction(std::stack<CSSParser::ASTNode *>*);

                static void             initialASTNode(ASTNode *target, Selector* head, ASTNode* left, ASTNode* right);

                static void             pushOperatedElement(std::stack<ASTNode *>&, Selector* head);

                bool                    parse();

                void                    prepareByFile(const std::string& filePath);

                void                    prepareByString(const std::string& cssString);

                void                    clean();

                bool                    startSelector(CSSTokenType);

                bool                    tokenHasInfo(CSSTokenType);

                bool                    topHaveSign(std::stack<Selector *>&);

                Selector*               getSelector(Lex::CSSToken* token);

                PseudoSelector::Parameter* getFunctionParamenter();

                std::list<ASTNode *>    createATS(std::stack<Selector *>&);

                void                    pushSign(std::stack<Selector *>&, SignSelector::SignType);

                void                    buildReversePolishNotation(std::stack<ASTNode*>& operatorStack, std::stack<ASTNode*>& operandStack);

                void                    RMLtranverseAST(ASTNode *root, treeTranverseAction action);

                void                    LRMtranverseAST(ASTNode *root, treeTranverseAction action);

                void                    LMRtranverseAST(ASTNode *root, treeTranverseAction action);

                void                    MLRtranverseAST(ASTNode *root, treeTranverseWithUserDataAction action, void *userData);
    private:
        Lex*                        m_lexer;
        CSSParserStatus             m_status;
        std::string                 m_hostCssFile;
        // v4.0.1 修复:由 std::set<Selector*> 改为 std::vector<Selector*>,
        // 原因:set 无自定义比较器时按指针地址排序,跨进程启动随机,
        // 导致下游 cssInfos 顺序随机 → tag.params 随机 → pageSize 抖动。
        // vector 保持插入顺序(= CSS 源码顺序),是确定性的。
        // 不需要去重:每次 new ClassSelector 等都是新对象,指针必不同。
        std::vector<Selector *>     m_selectors;
        std::list<KeywordItem *>    m_keywords;
        std::list<Selector *> 		 m_signSelecors;
    };
}

#endif /* CSSParser_hpp */
