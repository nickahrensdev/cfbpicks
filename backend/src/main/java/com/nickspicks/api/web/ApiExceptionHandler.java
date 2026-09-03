package com.nickspicks.api.web;

import com.nickspicks.api.cfbd.CfbdUnavailableException;
import com.nickspicks.api.group.GroupExceptions;
import com.nickspicks.api.ingest.GameNotGradableException;
import com.nickspicks.api.pick.PickExceptions;
import com.nickspicks.api.pick.PickExceptions.InvalidPickException;
import com.nickspicks.api.pick.PickExceptions.PickWindowClosedException;
import com.nickspicks.api.pick.PickExceptions.WeeklyLimitReachedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 7807 responses. Every domain failure carries a machine-readable
 * {@code code} so the UI can tell "too late" from "already have ten" without
 * parsing prose.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(PickWindowClosedException.class)
    public ProblemDetail handleWindowClosed(PickWindowClosedException ex) {
        return problem(HttpStatus.CONFLICT, "PICK_WINDOW_CLOSED", ex.getMessage());
    }

    @ExceptionHandler(WeeklyLimitReachedException.class)
    public ProblemDetail handleWeeklyLimit(WeeklyLimitReachedException ex) {
        return problem(HttpStatus.CONFLICT, "WEEKLY_LIMIT_REACHED", ex.getMessage());
    }

    @ExceptionHandler(PickExceptions.LineMovedException.class)
    public ProblemDetail handleLineMoved(PickExceptions.LineMovedException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "LINE_MOVED", ex.getMessage());
        // The UI shows this so the member can accept the new number knowingly.
        problem.setProperty("currentLine", ex.getCurrentLine());
        return problem;
    }

    @ExceptionHandler(InvalidPickException.class)
    public ProblemDetail handleInvalidPick(InvalidPickException ex) {
        return problem(HttpStatus.CONFLICT, "INVALID_PICK", ex.getMessage());
    }

    @ExceptionHandler(MeController.UsernameTakenException.class)
    public ProblemDetail handleUsernameTaken(MeController.UsernameTakenException ex) {
        return problem(HttpStatus.CONFLICT, "USERNAME_TAKEN", ex.getMessage());
    }

    @ExceptionHandler(GroupExceptions.InvalidGroupSettingsException.class)
    public ProblemDetail handleInvalidGroupSettings(GroupExceptions.InvalidGroupSettingsException ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_GROUP_SETTINGS", ex.getMessage());
    }

    @ExceptionHandler(GroupExceptions.PasswordRequiredException.class)
    public ProblemDetail handleGroupPasswordRequired(GroupExceptions.PasswordRequiredException ex) {
        return problem(HttpStatus.BAD_REQUEST, "GROUP_PASSWORD_REQUIRED", ex.getMessage());
    }

    @ExceptionHandler(GroupExceptions.PasswordIncorrectException.class)
    public ProblemDetail handleGroupPasswordIncorrect(GroupExceptions.PasswordIncorrectException ex) {
        return problem(HttpStatus.FORBIDDEN, "GROUP_PASSWORD_INCORRECT", ex.getMessage());
    }

    @ExceptionHandler(GroupExceptions.JoinsClosedException.class)
    public ProblemDetail handleJoinsClosed(GroupExceptions.JoinsClosedException ex) {
        return problem(HttpStatus.FORBIDDEN, "GROUP_JOINS_CLOSED", ex.getMessage());
    }

    @ExceptionHandler(GroupExceptions.AlreadyMemberException.class)
    public ProblemDetail handleAlreadyMember(GroupExceptions.AlreadyMemberException ex) {
        return problem(HttpStatus.CONFLICT, "ALREADY_A_MEMBER", ex.getMessage());
    }

    @ExceptionHandler(CfbdUnavailableException.class)
    public ProblemDetail handleUpstream(CfbdUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(GameNotGradableException.class)
    public ProblemDetail handleGameNotGradable(GameNotGradableException ex) {
        return problem(HttpStatus.CONFLICT, "GAME_NOT_GRADABLE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
