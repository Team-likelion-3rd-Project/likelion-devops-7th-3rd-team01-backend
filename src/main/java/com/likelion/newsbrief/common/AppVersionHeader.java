package com.likelion.newsbrief.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 모든 응답에 실행 중인 커밋을 X-App-Version으로 붙인다. 클러스터 밖에서 도는 배포가 배포 후 버전을 확인한다 */
@Component
class AppVersionHeader implements Filter {

  private final String commit;

  AppVersionHeader(@Value("${info.commit}") String commit) {
    this.commit = commit;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    ((HttpServletResponse) response).setHeader("X-App-Version", commit);
    chain.doFilter(request, response);
  }
}
